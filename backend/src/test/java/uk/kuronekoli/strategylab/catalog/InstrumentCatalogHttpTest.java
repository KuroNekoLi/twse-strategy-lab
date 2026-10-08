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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/** Embedded local HTTP; the source transport returns synthetic JSON and never contacts TWSE. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(InstrumentCatalogHttpTest.SyntheticCatalog.class)
class InstrumentCatalogHttpTest {
  @Value("${local.server.port}") int port;
  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper mapper = JsonMapper.builder().build();
  @TestConfiguration(proxyBeanMethods = false)
  static class SyntheticCatalog {
    @Bean @Primary InstrumentCatalogClient fixtureCatalogClient(JsonMapper mapper) {
      return new InstrumentCatalogClient(mapper, URI.create("https://example.invalid/company"), URI.create("https://example.invalid/fund"), Duration.ofSeconds(60), Clock.systemUTC(), uri ->
          new InstrumentCatalogClient.TransportResponse(200, uri.getPath().equals("/company") ? InstrumentCatalogTest.COMPANIES : InstrumentCatalogTest.FUNDS));
    }
  }
  private HttpResponse<String> get(String query) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/instruments" + query)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }
  @Test void realCatalogGetUsesQueryDefaultsAndWhitelistedResponse() throws Exception {
    var response = get("?query=2330"); assertEquals(200, response.statusCode(), response.body());
    var json = mapper.readTree(response.body()); assertEquals(20, json.path("limit").asInt()); assertEquals(1, json.path("totalMatches").asInt());
    var item = json.path("items").get(0); assertEquals(4, item.size()); assertEquals("2330", item.path("code").asText()); assertEquals("STOCK", item.path("kind").asText());
    assertEquals("2026-10-08", item.path("asOf").asText()); assertEquals(2, json.path("sources").size()); assertFalse(response.body().contains("PRIVATE_"));
  }
  @Test void missingBlankExcessiveAndNonNumericLimitsHaveCatalogCodes() throws Exception {
    for (String query : new String[]{"", "?query=", "?query=2330&limit=51", "?query=2330&limit=NaN"}) {
      var response = get(query); assertEquals(400, response.statusCode(), response.body());
      var json = mapper.readTree(response.body()); assertEquals("INVALID_CATALOG_QUERY", json.path("code").asText()); assertTrue(json.path("error").isTextual());
    }
  }
}
