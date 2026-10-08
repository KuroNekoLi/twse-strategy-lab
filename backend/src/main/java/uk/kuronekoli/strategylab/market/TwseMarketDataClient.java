package uk.kuronekoli.strategylab.market;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.backtest.BacktestValidation;

@Component
public class TwseMarketDataClient {
  private static final Logger log = LoggerFactory.getLogger(TwseMarketDataClient.class);
  private final JsonMapper mapper;
  private final URI endpoint;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  public TwseMarketDataClient(JsonMapper mapper, @Value("${app.twse.base-url}") String endpoint) {
    this.mapper = mapper; this.endpoint = URI.create(endpoint);
  }

  public List<DailyBar> load(String symbol, YearMonth first, YearMonth last) {
    List<DailyBar> bars = new ArrayList<>();
    for (YearMonth groupStart = first; !groupStart.isAfter(last); groupStart = groupStart.plusMonths(3)) {
      List<YearMonth> group = new ArrayList<>();
      for (int i = 0; i < 3; i++) { YearMonth month = groupStart.plusMonths(i); if (!month.isAfter(last)) group.add(month); }
      List<DailyBar> batch = group.parallelStream().flatMap(month -> loadMonth(symbol, month).stream()).toList();
      bars.addAll(batch);
      if (!groupStart.plusMonths(3).isAfter(last)) try { Thread.sleep(120); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("行情請求已中斷。", e); }
    }
    BacktestValidation.bars(bars);
    return List.copyOf(bars);
  }

  private List<DailyBar> loadMonth(String symbol, YearMonth month) {
    String query = "?date=" + month.atDay(1).toString().replace("-", "") + "&stockNo=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "&response=json";
    // URI.resolve("?query") treats the endpoint's last path segment as a file
    // and replaces it, dropping STOCK_DAY. Append the query to preserve it.
    URI uri = URI.create(endpoint.toString() + query);
    try {
      HttpResponse<String> response = fetch(uri);
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw BacktestException.upstream(symbol + "／" + month + "：證交所行情服务回應 " + response.statusCode() + "。");
      JsonNode root;
      try {
        root = mapper.readTree(response.body());
      } catch (Exception e) {
        log.warn("TWSE response was not valid JSON: symbol={}, month={}, status={}, contentType={}, server={}, upstreamRequestId={}, bodyLength={}, firstCharacter={}",
            symbol, month, response.statusCode(),
            response.headers().firstValue("content-type").orElse("missing"),
            response.headers().firstValue("server").orElse("missing"),
            response.headers().firstValue("x-request-id").orElse("missing"),
            response.body().length(), response.body().isEmpty() ? "empty" : response.body().substring(0, 1), e);
        throw e;
      }
      return parseMonthResponse(symbol, month, root);
    } catch (BacktestException e) {
      throw e;
    } catch (IllegalStateException e) {
      log.warn("TWSE data request failed: symbol={}, month={}, exceptionType={}, reason={}",
          symbol, month, e.getClass().getName(), e.getMessage());
      throw e;
    } catch (Exception e) {
      Throwable root = rootCause(e);
      log.warn("TWSE data request failed: symbol={}, month={}, exceptionType={}, rootCauseType={}, rootCauseReason={}",
          symbol, month, e.getClass().getName(), root.getClass().getName(), root.getMessage(), e);
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw BacktestException.upstream(symbol + "／" + month + "：無法讀取證交所行情，請稍後再試。");
    }
  }

  /** Strict package-visible parser allows offline synthetic provider-shape tests. */
  List<DailyBar> parseMonthResponse(String symbol, YearMonth month, JsonNode root) {
    String context = symbol + "／" + month + "：";
    if (root == null || !root.isObject() || !root.path("stat").isTextual() || !"OK".equals(root.path("stat").asText()))
      throw new BacktestException("UPSTREAM_MONTH_STATUS_UNKNOWN", context + "行情狀態非 OK；上市、停牌及缺漏原因未知，已停止計算。", 502);
    JsonNode data = root.path("data");
    if (!data.isArray() || data.isEmpty()) throw new BacktestException("UPSTREAM_EMPTY_MONTH", context + "月份資料為空或格式不正確，已停止計算。", 502);
    if (data.size() > 31) throw BacktestException.data(context + "月份行情筆數超出上限。");
    List<DailyBar> rows = new ArrayList<>();
    for (JsonNode row : data) {
      try {
        if (!row.isArray() || row.size() < 7 || !row.get(0).isTextual() || !row.get(6).isTextual()) throw new IllegalArgumentException("row shape");
        String dateText = row.get(0).asText().trim();
        if (!dateText.matches("\\d{2,4}/\\d{1,2}/\\d{1,2}")) throw new IllegalArgumentException("date shape");
        String[] dateParts = dateText.split("/");
        int year = Integer.parseInt(dateParts[0]); if (year < 1911) year += 1911;
        LocalDate date = LocalDate.of(year, Integer.parseInt(dateParts[1]), Integer.parseInt(dateParts[2]));
        if (!YearMonth.from(date).equals(month)) throw new IllegalArgumentException("wrong month");
        String price = row.get(6).asText().trim();
        if (!price.matches("(?:\\d+|\\d{1,3}(?:,\\d{3})+)(?:\\.\\d+)?")) throw new IllegalArgumentException("price shape");
        rows.add(new DailyBar(date, new BigDecimal(price.replace(",", ""))));
      } catch (RuntimeException e) {
        throw BacktestException.data(context + "行情列含無效日期或收盤價；不跳過錯誤列，已停止計算。");
      }
    }
    try { BacktestValidation.bars(rows); } catch (BacktestException e) { throw BacktestException.data(context + e.getMessage()); }
    return List.copyOf(rows);
  }

  private Throwable rootCause(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null && root.getCause() != root) root = root.getCause();
    return root;
  }

  private HttpResponse<String> fetch(URI start) throws Exception {
    URI current = start;
    for (int hop = 0; hop < 4; hop++) {
      HttpResponse<String> response = null;
      for (int attempt = 0; attempt < 3; attempt++) {
        HttpRequest request = HttpRequest.newBuilder(current).timeout(java.time.Duration.ofSeconds(20))
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .header("Referer", "https://www.twse.com.tw/")
            .header("User-Agent", "Mozilla/5.0 (compatible; TWSEStrategyLab/1.0)").GET().build();
        response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        boolean redirectWithoutLocation = response.statusCode() >= 300 && response.statusCode() <= 399
            && response.headers().firstValue("location").isEmpty();
        if (!redirectWithoutLocation || attempt == 2) break;
        Thread.sleep(250L * (attempt + 1));
      }
      if (response.statusCode() < 300 || response.statusCode() > 399) return response;
      var locationHeader = response.headers().firstValue("location");
      if (locationHeader.isEmpty()) {
        throw new IllegalStateException("證交所回應轉址但未提供目的位址（status=" + response.statusCode()
            + ", server=" + response.headers().firstValue("server").orElse("missing")
            + ", requestId=" + response.headers().firstValue("x-request-id").orElse("missing")
            + ", contentType=" + response.headers().firstValue("content-type").orElse("missing")
            + ", bodyLength=" + response.body().length() + "）。");
      }
      String location = locationHeader.get();
      URI next = current.resolve(location);
      String host = next.getHost() == null ? "" : next.getHost().toLowerCase();
      if (!"https".equalsIgnoreCase(next.getScheme()) || !(host.equals("twse.com.tw") || host.endsWith(".twse.com.tw"))) throw new IllegalStateException("證交所行情服務導向非證交所網址，已停止請求。");
      current = next;
    }
    throw new IllegalStateException("證交所行情服務轉址次數過多，請稍後再試。");
  }
}
