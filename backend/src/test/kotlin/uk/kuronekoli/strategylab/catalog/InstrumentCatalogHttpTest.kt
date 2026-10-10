package uk.kuronekoli.strategylab.catalog

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper

/** Embedded local HTTP; synthetic source transport never contacts TWSE. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["app.market-data.ingestion-enabled=false"])
@ActiveProfiles("local")
@Import(InstrumentCatalogHttpTest.SyntheticCatalog::class)
class InstrumentCatalogHttpTest {
    @Value("\${local.server.port}") private var port: Int = 0
    @Autowired private lateinit var jdbc: JdbcTemplate
    private val http = HttpClient.newHttpClient()
    private val mapper = JsonMapper.builder().build()
    @TestConfiguration(proxyBeanMethods = false)
    class SyntheticCatalog {
        @Bean @Primary fun fixtureCatalogClient(mapper: JsonMapper): InstrumentCatalogClient = InstrumentCatalogClient(mapper, URI.create("https://example.invalid/company"), URI.create("https://example.invalid/fund"), URI.create("https://example.invalid/tpex"), Duration.ofSeconds(60), Clock.systemUTC()) { uri ->
            InstrumentCatalogClient.TransportResponse(200, when (uri.path) { "/company" -> InstrumentCatalogTest.COMPANIES; "/fund" -> InstrumentCatalogTest.FUNDS; else -> InstrumentCatalogTest.TPEX_COMPANIES })
        }
    }
    private fun get(query: String): HttpResponse<String> = http.send(HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/instruments$query")).GET().build(), HttpResponse.BodyHandlers.ofString())
    @Test fun realCatalogGetUsesQueryDefaultsAndWhitelistedResponse() {
        val response = get("?query=2330"); assertEquals(200, response.statusCode(), response.body())
        val json = mapper.readTree(response.body()); assertEquals(20, json.path("limit").asInt()); assertEquals(1, json.path("totalMatches").asInt())
        val item = json.path("items").get(0); assertEquals("2330", item.path("code").asText()); assertEquals("STOCK", item.path("kind").asText())
        assertEquals("TWSE", item.path("market").asText()); assertTrue(item.path("backtestSupported").asBoolean()); assertEquals("2026-10-08", item.path("asOf").asText())
        assertEquals(3, json.path("sources").size()); assertFalse(response.body().contains("PRIVATE_"))
        assertTrue(jdbc.queryForObject("select count(*) from catalog_instrument", Int::class.java)!! >= 7)
        assertEquals(3, jdbc.queryForObject("select count(*) from catalog_source", Int::class.java))
    }
    @Test fun prefixAndTraditionalChineseNameQueriesUsePersistedTaiwanCatalog() {
        val byCode = mapper.readTree(get("?query=005").body())
        assertTrue(byCode.path("items").any { it.path("code").asText() == "0050" })
        val byName = mapper.readTree(get("?query=台").body())
        assertTrue(byName.path("items").any { it.path("name").asText() == "台積電" })
        assertTrue(byName.path("items").any { it.path("code").asText() == "1240" && !it.path("backtestSupported").asBoolean() })
    }
    @Test fun missingBlankExcessiveAndNonNumericLimitsHaveCatalogCodes() {
        listOf("", "?query=", "?query=2330&limit=51", "?query=2330&limit=NaN").forEach { query ->
            val response = get(query); assertEquals(400, response.statusCode(), response.body())
            val json = mapper.readTree(response.body()); assertEquals("INVALID_CATALOG_QUERY", json.path("code").asText()); assertTrue(json.path("error").isTextual)
        }
    }
}
