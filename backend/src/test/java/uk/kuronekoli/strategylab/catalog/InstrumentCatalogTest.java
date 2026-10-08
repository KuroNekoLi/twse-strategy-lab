package uk.kuronekoli.strategylab.catalog;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Synthetic responses and injected transport only; no source GET is executed. */
class InstrumentCatalogTest {
  private static final URI COMPANY = URI.create("https://example.invalid/company"), FUND = URI.create("https://example.invalid/fund");
  private final JsonMapper mapper = JsonMapper.builder().build();
  static final String COMPANIES = "[{\"公司代號\":\"2330\",\"公司名稱\":\"台積電\",\"出表日期\":\"1151008\",\"董事長\":\"PRIVATE_PERSON\",\"電話\":\"PRIVATE_PHONE\"},"
      + "{\"公司代號\":\"2303\",\"公司名稱\":\"聯電\",\"出表日期\":\"20261008\"}]";
  static final String FUNDS = "[{\"基金代號\":\"0050\",\"基金簡稱\":\"測試台灣50\",\"出表日期\":\"2026-10-07\",\"經理人\":\"PRIVATE_MANAGER\"},"
      + "{\"基金代號\":\"00679B\",\"基金簡稱\":\"測試債券\",\"出表日期\":\"115/10/08\"}]";
  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-09T00:00:00Z");
    void advance(long seconds) { now = now.plusSeconds(seconds); }
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return this; }
    @Override public Instant instant() { return now; }
  }
  private InstrumentCatalogClient client(Clock clock, InstrumentCatalogClient.Transport transport) {
    return new InstrumentCatalogClient(mapper, COMPANY, FUND, Duration.ofSeconds(60), clock, transport);
  }
  private InstrumentCatalogClient validClient() {
    return client(new MutableClock(), uri -> new InstrumentCatalogClient.TransportResponse(200, uri.equals(COMPANY) ? COMPANIES : FUNDS));
  }
  @Test void whitelistedSearchReturnsDeterministicCodeNameKindAndParsedAsOf() {
    var service = new InstrumentCatalogService(validClient());
    var response = service.search("台", 20);
    assertEquals(List.of("2330", "0050"), response.items().stream().map(i -> i.code()).toList());
    assertEquals("STOCK", response.items().get(0).kind()); assertEquals("FUND", response.items().get(1).kind());
    assertEquals("2026-10-08", response.items().get(0).asOf()); assertEquals("2026-10-07", response.items().get(1).asOf());
    assertEquals(response, service.search("台", 20));
    String json = mapper.writeValueAsString(response);
    for (String privateField : List.of("PRIVATE_PERSON", "PRIVATE_PHONE", "PRIVATE_MANAGER", "董事長", "電話", "經理人")) assertFalse(json.contains(privateField), privateField);
    assertEquals(2, response.sources().size()); assertEquals("https://data.gov.tw/dataset/18419", response.sources().get(0).datasetUrl());
    assertTrue(response.sources().get(0).license().contains("OGDL v1.0")); assertEquals("MONTHLY", response.sources().get(0).updateFrequency());
    assertEquals("2026-10-07", response.sources().get(1).asOfFrom()); assertEquals("2026-10-08", response.sources().get(1).asOfTo());
    assertEquals("2026-10-09T00:00:00Z", response.sources().get(0).fetchedAt());
  }
  @Test void fullWidthQueriesSuffixCodesLimitsAndNoMatchesAreHandled() {
    var service = new InstrumentCatalogService(validClient());
    var exact = service.search(" ２３３０ ", 1); assertEquals("2330", exact.query()); assertEquals("2330", exact.items().get(0).code());
    assertEquals("00679B", service.search("00679b", 20).items().get(0).code());
    var limited = service.search("電", 1); assertEquals(2, limited.totalMatches()); assertEquals(1, limited.items().size());
    assertEquals("2303", limited.items().get(0).code()); assertTrue(service.search("沒有符合名稱", 20).items().isEmpty());
  }
  @Test void invalidQueryAndLimitFailBeforeAnySourceCall() {
    AtomicInteger calls = new AtomicInteger(); var service = new InstrumentCatalogService(client(new MutableClock(), uri -> { calls.incrementAndGet(); return null; }));
    for (String query : new String[]{null, "", "  ", "x".repeat(61), "ﬃ".repeat(60), "test\n", "\u0000"})
      assertEquals("INVALID_CATALOG_QUERY", assertThrows(CatalogException.class, () -> service.search(query, 20)).code());
    for (int limit : new int[]{0, -1, 51, Integer.MAX_VALUE}) assertEquals(400, assertThrows(CatalogException.class, () -> service.search("台", limit)).status());
    assertEquals(0, calls.get());
  }
  @Test void perResourceTtlReusesOnlyNormalizedSnapshotsAndRefreshesAtExpiry() {
    MutableClock clock = new MutableClock(); AtomicInteger companyCalls = new AtomicInteger(), fundCalls = new AtomicInteger();
    AtomicReference<String> companies = new AtomicReference<>(COMPANIES);
    var client = client(clock, uri -> {
      if (uri.equals(COMPANY)) { companyCalls.incrementAndGet(); return new InstrumentCatalogClient.TransportResponse(200, companies.get()); }
      fundCalls.incrementAndGet(); return new InstrumentCatalogClient.TransportResponse(200, FUNDS);
    });
    var service = new InstrumentCatalogService(client);
    service.search("台", 20); clock.advance(59); service.search("00679B", 20);
    assertEquals(1, companyCalls.get()); assertEquals(1, fundCalls.get());
    companies.set(COMPANIES.replace("台積電", "測試新名稱").replace("1151008", "1151009"));
    clock.advance(1); var refreshed = service.search("2330", 20);
    assertEquals(2, companyCalls.get()); assertEquals(2, fundCalls.get()); assertEquals("測試新名稱", refreshed.items().get(0).name());
    assertEquals("2026-10-09", refreshed.items().get(0).asOf()); assertEquals("2026-10-09T00:01:00Z", refreshed.sources().get(0).fetchedAt());
  }
  @Test void expiredSnapshotIsNeverSilentlyReturnedAfterRefreshFailure() {
    MutableClock clock = new MutableClock(); AtomicInteger companyCalls = new AtomicInteger(); AtomicReference<Integer> status = new AtomicReference<>(200);
    var service = new InstrumentCatalogService(client(clock, uri -> {
      if (uri.equals(COMPANY)) { companyCalls.incrementAndGet(); return new InstrumentCatalogClient.TransportResponse(status.get(), COMPANIES); }
      return new InstrumentCatalogClient.TransportResponse(200, FUNDS);
    }));
    service.search("2330", 20); clock.advance(60); status.set(503);
    var error = assertThrows(CatalogException.class, () -> service.search("2330", 20)); assertEquals("CATALOG_UPSTREAM_UNAVAILABLE", error.code());
    assertThrows(CatalogException.class, () -> service.search("2330", 20)); assertEquals(3, companyCalls.get());
  }
  @Test void malformedEmptyDuplicateMissingOrInvalidRecordsFailWholeResource() {
    var client = validClient();
    for (String body : new String[]{"{invalid", "{}", "null", "[null]", COMPANIES + "[]", COMPANIES.replace("\"公司代號\":\"2330\"", "\"公司代號\":\"2330\",\"公司代號\":\"2300\""), "[{\"公司代號\":\"2330\",\"公司名稱\":\"台積電\"}]",
        COMPANIES.replace("1151008", "1150230"), COMPANIES.replace("1151008", "unknown"), COMPANIES.replace("2330", "bad"),
        COMPANIES.replace("2303", "2330"), COMPANIES.replace("台積電", ""), COMPANIES.replace("\"2330\"", "2330")}) {
      var error = assertThrows(CatalogException.class, () -> client.parse(InstrumentCatalogClient.Resource.COMPANY, body));
      assertEquals("CATALOG_SCHEMA_INVALID", error.code()); assertEquals(502, error.status()); assertFalse(error.getMessage().contains("PRIVATE_"));
    }
    assertEquals("CATALOG_DATA_EMPTY", assertThrows(CatalogException.class, () -> client.parse(InstrumentCatalogClient.Resource.COMPANY, "[]")).code());
  }
  @Test void oneResourceFailurePreventsPartialSearchAndCodeConflictFailsClosed() {
    var failure = new InstrumentCatalogService(client(new MutableClock(), uri -> new InstrumentCatalogClient.TransportResponse(200, uri.equals(COMPANY) ? COMPANIES : "[]")));
    assertEquals("CATALOG_DATA_EMPTY", assertThrows(CatalogException.class, () -> failure.search("2330", 20)).code());
    var conflict = new InstrumentCatalogService(client(new MutableClock(), uri -> new InstrumentCatalogClient.TransportResponse(200, uri.equals(COMPANY) ? COMPANIES : FUNDS.replace("0050", "2330"))));
    assertEquals("CATALOG_CODE_CONFLICT", assertThrows(CatalogException.class, () -> conflict.search("2330", 20)).code());
  }
  @Test void transportFailureAndNonSuccessStatusAreSanitized() {
    var failure = new InstrumentCatalogService(client(new MutableClock(), uri -> { throw new IllegalStateException("SECRET_PROVIDER_BODY"); }));
    var error = assertThrows(CatalogException.class, () -> failure.search("2330", 20)); assertEquals("CATALOG_UPSTREAM_UNAVAILABLE", error.code()); assertFalse(error.getMessage().contains("SECRET"));
    var status = new InstrumentCatalogService(client(new MutableClock(), uri -> new InstrumentCatalogClient.TransportResponse(302, COMPANIES)));
    assertEquals(502, assertThrows(CatalogException.class, () -> status.search("2330", 20)).status());
  }
}
