package uk.kuronekoli.strategylab.api;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.market.TwseMarketDataClient;

/** Real local HTTP + Spring startup, with a synthetic primary market source; no live calls. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.twse.base-url=https://example.invalid/STOCK_DAY")
@ActiveProfiles("local")
@Import(BacktestHttpContractTest.SyntheticMarket.class)
class BacktestHttpContractTest {
  @Value("${local.server.port}") int port;
  @Autowired JdbcTemplate jdbc;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper mapper = JsonMapper.builder().build();
  @TestConfiguration(proxyBeanMethods = false)
  static class SyntheticMarket {
    @Bean @Primary TwseMarketDataClient fixtureMarketClient(JsonMapper mapper) {
      return new TwseMarketDataClient(mapper, "https://example.invalid/STOCK_DAY") {
        @Override public List<DailyBar> load(String symbol, YearMonth first, YearMonth last) {
          return java.util.stream.IntStream.range(0, 5).mapToObj(i -> new DailyBar(LocalDate.of(2024, 1, 1).plusDays(i), 100)).toList();
        }
      };
    }
  }
  private String body(String initial) {
    return "{\"symbol\":\"2330\",\"from\":\"2024-01-01\",\"to\":\"2024-01-05\",\"strategy\":\"ma-crossover\",\"fastWindow\":2,\"slowWindow\":3,\"initialCapital\":" + initial
        + ",\"monthlyContribution\":0,\"commissionRate\":0.001425,\"sellTaxRate\":0.003,\"useMarketTaxDefaults\":false}";
  }
  private HttpResponse<String> post(String body) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/backtests"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }
  @Test void contextStartsAndCompatiblePostReturnsExactNumericLedgerAndResearchMetadata() throws Exception {
    var response = post(body("1000")); assertEquals(200, response.statusCode(), response.body());
    var json = mapper.readTree(response.body()); assertEquals("2330", json.path("symbol").asText());
    assertEquals("LIMITED_RESEARCH", json.path("status").asText()); assertEquals("BLOCKED", json.path("releaseStatus").asText());
    assertEquals("NEXT_CLOSE_PROXY", json.path("metadata").path("executionModel").asText());
    assertEquals(3, json.path("results").size());
    var dca = json.path("results").get(1); assertTrue(dca.path("endingValue").isNumber()); assertEquals("998.72", dca.path("endingValue").asText());
    assertEquals(9, dca.path("trades").get(0).path("quantity").asInt()); assertEquals("1.28", dca.path("trades").get(0).path("fee").asText());
    var hold = json.path("results").get(2); assertEquals("2330-buy-and-hold", hold.path("key").asText());
    assertEquals("998.72", hold.path("endingValue").asText()); assertEquals(9, hold.path("dailyEquity").get(0).path("shares").asInt());
    assertTrue(hold.path("annualizedRealizedVolatility").isNumber());
    assertEquals("2024-01-01", dca.path("series").get(0).path("date").asText());
    assertFalse(response.body().contains("Infinity")); assertFalse(response.body().contains("NaN"));
  }
  @Test void databaseHealthExecutesAQueryAgainstTheConfiguredDatasource() throws Exception {
    var response = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/health/database")).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    var json = mapper.readTree(response.body());
    assertEquals("ok", json.path("status").asText());
    assertEquals("ok", json.path("database").asText());
  }
  @Test void localProfileCreatesReplayTablesAndPersistsDecisionWithoutChangingApiShape() throws Exception {
    assertEquals(2, jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = 'PUBLIC' and table_name in ('REPLAY_SESSION', 'REPLAY_DECISION')", Integer.class));
    var created = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/replay/sessions"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}" )).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, created.statusCode(), created.body());
    var start = mapper.readTree(created.body());
    String id = start.path("sessionId").asText();
    assertTrue(start.path("observation").isObject());
    var decision = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/replay/sessions/" + id + "/decisions"))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"action\":\"BUY\",\"shares\":2}" )).build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, decision.statusCode(), decision.body());
    assertEquals(1, mapper.readTree(decision.body()).path("events").size());
    var resumed = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/replay/sessions/" + id)).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertEquals(200, resumed.statusCode(), resumed.body());
    assertEquals(1, mapper.readTree(resumed.body()).path("events").size());
    assertEquals(1, jdbc.queryForObject("select count(*) from replay_session where id = ?", Integer.class, id));
    assertEquals(1, jdbc.queryForObject("select count(*) from replay_decision where session_id = ?", Integer.class, id));
  }
  @Test void httpNullMoneyAndMalformedJsonReturnCodeAndOriginalErrorString() throws Exception {
    for (String invalid : new String[]{body("null"), "{invalid"}) {
      var response = post(invalid); assertEquals(400, response.statusCode());
      var json = mapper.readTree(response.body()); assertEquals("INVALID_INPUT", json.path("code").asText()); assertTrue(json.path("error").isTextual());
    }
  }
}
