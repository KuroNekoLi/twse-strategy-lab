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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TwseMarketDataClient {
  private final JsonMapper mapper;
  private final URI endpoint;
  private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
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
    return bars.stream().sorted(Comparator.comparing(DailyBar::date)).toList();
  }

  private List<DailyBar> loadMonth(String symbol, YearMonth month) {
    String query = "?date=" + month.atDay(1).toString().replace("-", "") + "&stockNo=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "&response=json";
    URI uri = endpoint.resolve(query);
    try {
      HttpResponse<String> response = fetch(uri);
      if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("證交所行情服務回應 " + response.statusCode() + "（" + month + "）。");
      JsonNode root = mapper.readTree(response.body());
      String stat = root.path("stat").asText("");
      if (!"OK".equals(stat)) {
        if (stat.isBlank() || stat.matches(".*(查詢日期小於|查詢日期大於|查無資料|無符合).*")) return List.of();
        throw new IllegalStateException("證交所暫時無法提供 " + month + " 的行情：" + stat);
      }
      List<DailyBar> rows = new ArrayList<>();
      for (JsonNode row : root.path("data")) {
        if (row.size() < 7) continue;
        String[] dateParts = row.get(0).asText().split("/");
        if (dateParts.length != 3) continue;
        int year = Integer.parseInt(dateParts[0]); if (year < 1911) year += 1911;
        double close = Double.parseDouble(row.get(6).asText().replace(",", ""));
        if (close > 0) rows.add(new DailyBar(LocalDate.of(year, Integer.parseInt(dateParts[1]), Integer.parseInt(dateParts[2])), close));
      }
      return rows;
    } catch (IllegalStateException e) { throw e; }
    catch (Exception e) { throw new IllegalStateException("無法讀取證交所 " + month + " 行情，請稍後再試。", e); }
  }

  private HttpResponse<String> fetch(URI start) throws Exception {
    URI current = start;
    for (int hop = 0; hop < 4; hop++) {
      HttpRequest request = HttpRequest.newBuilder(current).timeout(java.time.Duration.ofSeconds(20))
          .header("Accept", "application/json, text/plain, */*")
          .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
          .header("Referer", "https://www.twse.com.tw/")
          .header("User-Agent", "Mozilla/5.0 (compatible; TWSEStrategyLab/1.0)").GET().build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() < 300 || response.statusCode() > 399) return response;
      String location = response.headers().firstValue("location").orElseThrow(() -> new IllegalStateException("證交所回應轉址但未提供目的位址。"));
      URI next = current.resolve(location);
      String host = next.getHost() == null ? "" : next.getHost().toLowerCase();
      if (!"https".equalsIgnoreCase(next.getScheme()) || !(host.equals("twse.com.tw") || host.endsWith(".twse.com.tw"))) throw new IllegalStateException("證交所行情服務導向非證交所網址，已停止請求。");
      current = next;
    }
    throw new IllegalStateException("證交所行情服務轉址次數過多，請稍後再試。");
  }
}
