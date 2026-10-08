package uk.kuronekoli.strategylab.catalog;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.json.JsonMapper;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source;

@Component
public class InstrumentCatalogClient {
  private static final int MAX_ROWS = 20000;
  private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
  enum Resource {
    COMPANY("companies", "上市公司基本資料", "18419", "公司代號", "公司名稱", "STOCK", "TWSE", true),
    FUND("funds", "基金基本資料", "157399", "基金代號", "基金簡稱", "FUND", "TWSE", true),
    TPEX_COMPANY("tpexCompanies", "上櫃公司基本資料", "25036", "SecuritiesCompanyCode", "CompanyAbbreviation", "STOCK", "TPEX", false);
    final String id, title, dataset, codeField, nameField, kind, market;
    final boolean backtestSupported;
    Resource(String id, String title, String dataset, String codeField, String nameField, String kind, String market, boolean backtestSupported) {
      this.id = id; this.title = title; this.dataset = dataset; this.codeField = codeField; this.nameField = nameField;
      this.kind = kind; this.market = market; this.backtestSupported = backtestSupported;
    }
  }
  record TransportResponse(int status, String body) {}
  @FunctionalInterface interface Transport { TransportResponse get(URI uri) throws Exception; }
  record Snapshot(List<Instrument> rows, Source source, Instant expiresAt) {}
  private final JsonMapper mapper;
  private final Map<Resource, URI> endpoints;
  private final Duration ttl;
  private final Clock clock;
  private final Transport transport;
  // Only whitelisted normalized rows are retained in memory.
  private final Map<Resource, Snapshot> cache = new ConcurrentHashMap<>();
  private final Map<Resource, Object> locks = Map.of(Resource.COMPANY, new Object(), Resource.FUND, new Object(), Resource.TPEX_COMPANY, new Object());

  @Autowired
  public InstrumentCatalogClient(JsonMapper mapper,
      @Value("${app.catalog.company-url:https://openapi.twse.com.tw/v1/opendata/t187ap03_L}") String companyUrl,
      @Value("${app.catalog.fund-url:https://openapi.twse.com.tw/v1/opendata/t187ap47_L}") String fundUrl,
      @Value("${app.catalog.tpex-company-url:https://www.tpex.org.tw/openapi/v1/mopsfin_t187ap03_O}") String tpexCompanyUrl,
      @Value("${app.catalog.cache-ttl-seconds:900}") long ttlSeconds) {
    this(mapper, URI.create(companyUrl), URI.create(fundUrl), URI.create(tpexCompanyUrl), Duration.ofSeconds(ttlSeconds), Clock.systemUTC(), httpTransport());
  }
  InstrumentCatalogClient(JsonMapper mapper, URI companyUrl, URI fundUrl, Duration ttl, Clock clock, Transport transport) {
    this(mapper, companyUrl, fundUrl, URI.create("https://example.invalid/tpex-company"), ttl, clock, transport);
  }
  InstrumentCatalogClient(JsonMapper mapper, URI companyUrl, URI fundUrl, URI tpexCompanyUrl, Duration ttl, Clock clock, Transport transport) {
    if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofDays(1)) > 0) throw new IllegalArgumentException("Catalog cache TTL must be positive and at most one day");
    this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    this.endpoints = Map.of(Resource.COMPANY, companyUrl, Resource.FUND, fundUrl, Resource.TPEX_COMPANY, tpexCompanyUrl);
    this.ttl = ttl; this.clock = clock; this.transport = transport;
  }
  List<Snapshot> snapshots() { return List.of(snapshot(Resource.COMPANY), snapshot(Resource.FUND), snapshot(Resource.TPEX_COMPANY)); }
  private Snapshot snapshot(Resource resource) {
    synchronized (locks.get(resource)) {
      Instant now = clock.instant(); Snapshot saved = cache.get(resource);
      if (saved != null && now.isBefore(saved.expiresAt())) return saved;
      // Expired rows are never returned when refresh fails. A successfully loaded other resource is kept separately.
      cache.remove(resource);
      URI endpoint = endpoints.get(resource);
      TransportResponse response;
      try {
        response = transport.get(endpoint);
      } catch (Exception e) {
        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        throw CatalogException.upstream(resource.title);
      }
      if (response == null || response.status() < 200 || response.status() >= 300) throw CatalogException.upstream(resource.title);
      List<Instrument> rows = parse(resource, response.body());
      Instant fetched = clock.instant();
      String minDate = rows.stream().map(Instrument::asOf).min(String::compareTo).orElseThrow();
      String maxDate = rows.stream().map(Instrument::asOf).max(String::compareTo).orElseThrow();
      Source source = new Source(resource.id, resource.title, resource.market.equals("TPEX") ? "櫃買中心" : "臺灣證券交易所", "https://data.gov.tw/dataset/" + resource.dataset,
          endpoint.toString(), "政府資料開放授權條款第1版（OGDL v1.0）", "https://data.gov.tw/license", resource.market.equals("TPEX") ? "DAILY" : "MONTHLY",
          fetched.toString(), minDate, maxDate, ttl.toSeconds());
      Snapshot loaded = new Snapshot(rows, source, fetched.plus(ttl)); cache.put(resource, loaded); return loaded;
    }
  }
  List<Instrument> parse(Resource resource, String body) {
    if (body == null || body.isBlank() || body.length() > MAX_BODY_BYTES) throw CatalogException.schema(resource.title);
    JsonNode root;
    try { root = mapper.readTree(body); } catch (RuntimeException e) { throw CatalogException.schema(resource.title); }
    if (root == null || !root.isArray()) throw CatalogException.schema(resource.title);
    if (root.isEmpty()) throw new CatalogException("CATALOG_DATA_EMPTY", resource.title + "內容為空，名錄查詢已停止。", 502);
    if (root.size() > MAX_ROWS) throw CatalogException.schema(resource.title);
    List<Instrument> rows = new ArrayList<>(); Set<String> seen = new HashSet<>();
    for (JsonNode row : root) {
      if (!row.isObject()) throw CatalogException.schema(resource.title);
      String code = text(row, resource.codeField, resource), name = switch (resource) {
        case COMPANY -> optionalText(row, "公司簡稱", text(row, resource.nameField, resource));
        case TPEX_COMPANY -> optionalText(row, "CompanyAbbreviation", text(row, "CompanyName", resource));
        case FUND -> text(row, resource.nameField, resource);
      };
      if (!code.matches("[0-9]{4,6}[A-Z]?") || name.length() > 200 || name.codePoints().anyMatch(Character::isISOControl) || !seen.add(code)) throw CatalogException.schema(resource.title);
      String date = date(text(row, resource == Resource.TPEX_COMPANY ? "Date" : "出表日期", resource), resource);
      rows.add(new Instrument(code, name, resource.kind, date, resource.market, resource.backtestSupported));
    }
    rows.sort(Comparator.comparing(Instrument::code)); return List.copyOf(rows);
  }
  private String text(JsonNode row, String field, Resource resource) {
    JsonNode value = row.path(field);
    if (!value.isTextual() || value.asText().isBlank()) throw CatalogException.schema(resource.title);
    return value.asText().strip();
  }
  private String optionalText(JsonNode row, String field, String fallback) {
    JsonNode value = row.path(field);
    return value.isTextual() && !value.asText().isBlank() ? value.asText().strip() : fallback;
  }
  private String date(String value, Resource resource) {
    try {
      LocalDate date;
      if (value.matches("[0-9]{7}")) {
        date = LocalDate.of(Integer.parseInt(value.substring(0, 3)) + 1911, Integer.parseInt(value.substring(3, 5)), Integer.parseInt(value.substring(5, 7)));
      } else if (value.matches("[0-9]{8}")) {
        date = LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
      } else if (value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
        date = LocalDate.parse(value);
      } else if (value.matches("[0-9]{2,4}/[0-9]{1,2}/[0-9]{1,2}")) {
        String[] parts = value.split("/"); int year = Integer.parseInt(parts[0]);
        if (parts[0].length() < 4) year += 1911;
        date = LocalDate.of(year, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
      } else throw new IllegalArgumentException("Unrecognized stamp");
      if (date.isBefore(LocalDate.of(1912, 1, 1))) throw new IllegalArgumentException("Invalid stamp");
      return date.toString();
    } catch (RuntimeException e) { throw CatalogException.schema(resource.title); }
  }
  private static Transport httpTransport() {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    return uri -> {
      HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json")
          .header("User-Agent", "TWSEStrategyLab/1.0 catalog-research").GET().build();
      HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream stream = response.body()) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) return new TransportResponse(response.statusCode(), "");
        // HttpRequest's header timeout does not bound reads from an InputStream body.
        // Closing the stream on timeout prevents a slow body from holding the resource lock indefinitely.
        var reader = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "catalog-body-reader"); thread.setDaemon(true); return thread; });
        byte[] bytes;
        try { bytes = reader.submit(() -> stream.readNBytes(MAX_BODY_BYTES + 1)).get(20, TimeUnit.SECONDS); }
        finally { reader.shutdownNow(); }
        if (bytes.length > MAX_BODY_BYTES) throw new IllegalStateException("Catalog response too large");
        return new TransportResponse(response.statusCode(), new String(bytes, StandardCharsets.UTF_8));
      }
    };
  }
}
