package uk.kuronekoli.strategylab.catalog;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
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

/** Embedded local HTTP; the source transport returns synthetic JSON and never contacts TWSE. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(InstrumentCatalogHttpTest.SyntheticCatalog.class)
class InstrumentCatalogHttpTest {
  @Value("${local.server.port}") int port;
  @Autowired JdbcTemplate jdbc;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper mapper = JsonMapper.builder().build();
  @TestConfiguration(proxyBeanMethods = false)
  static class SyntheticCatalog {
    @Bean @Primary InstrumentCatalogClient fixtureCatalogClient(JsonMapper mapper) {
      return new InstrumentCatalogClient(mapper, URI.create("https://example.invalid/company"), URI.create("https://example.invalid/fund"), URI.create("https://example.invalid/tpex"), Duration.ofSeconds(60), Clock.systemUTC(), uri ->
          new InstrumentCatalogClient.TransportResponse(200, uri.getPath().equals("/company") ? InstrumentCatalogTest.COMPANIES
              : uri.getPath().equals("/fund") ? InstrumentCatalogTest.FUNDS : InstrumentCatalogTest.TPEX_COMPANIES));
    }
  }
  private HttpResponse<String> get(String query) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/instruments" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
  @Test void realCatalogGetUsesQueryDefaultsAndWhitelistedResponse() throws Exception {
    var response = get("?query=2330"); assertEquals(200, response.statusCode(), response.body());
    var json = mapper.readTree(response.body()); assertEquals(20, json.path("limit").asInt()); assertEquals(1, json.path("totalMatches").asInt());
    var item = json.path("items").get(0); assertEquals("2330", item.path("code").asText()); assertEquals("STOCK", item.path("kind").asText());
    assertEquals("TWSE", item.path("market").asText()); assertTrue(item.path("backtestSupported").asBoolean());
    assertEquals("2026-10-08", item.path("asOf").asText()); assertEquals(3, json.path("sources").size()); assertFalse(response.body().contains("PRIVATE_"));
    assertTrue(jdbc.queryForObject("select count(*) from catalog_instrument", Integer.class) >= 7);
    assertEquals(3, jdbc.queryForObject("select count(*) from catalog_source", Integer.class));
  }
  @Test void prefixAndTraditionalChineseNameQueriesUsePersistedTaiwanCatalog() throws Exception {
    var byCode = mapper.readTree(get("?query=005").body());
    assertTrue(java.util.stream.StreamSupport.stream(byCode.path("items").spliterator(), false)
        .anyMatch(item -> item.path("code").asText().equals("0050")));
    var byName = mapper.readTree(get("?query=台").body());
    assertTrue(java.util.stream.StreamSupport.stream(byName.path("items").spliterator(), false)
        .anyMatch(item -> item.path("name").asText().equals("台積電")));
    assertTrue(java.util.stream.StreamSupport.stream(byName.path("items").spliterator(), false)
        .anyMatch(item -> item.path("code").asText().equals("1240") && item.path("backtestSupported").asBoolean() == false));
  }
  @Test void missingBlankExcessiveAndNonNumericLimitsHaveCatalogCodes() throws Exception {
    for (String query : new String[]{"", "?query=", "?query=2330&limit=51", "?query=2330&limit=NaN"}) {
      var response = get(query); assertEquals(400, response.statusCode(), response.body());
      var json = mapper.readTree(response.body()); assertEquals("INVALID_CATALOG_QUERY", json.path("code").asText()); assertTrue(json.path("error").isTextual());
    }
  }
}
