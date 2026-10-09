package uk.kuronekoli.strategylab.api

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.YearMonth
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
import uk.kuronekoli.strategylab.market.DailyBar
import uk.kuronekoli.strategylab.market.TwseMarketDataClient

/** Real local HTTP + Spring startup, with a synthetic primary market source; no live calls. */
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["app.twse.base-url=https://example.invalid/STOCK_DAY", "app.market-history.enabled=true"]
)
@ActiveProfiles("local")
@Import(BacktestHttpContractTest.SyntheticMarket::class)
class BacktestHttpContractTest {
  @field:Value("${'$'}{local.server.port}")
  lateinit var port: String

  @Autowired
  lateinit var jdbc: JdbcTemplate

  private val http = HttpClient.newHttpClient()
  private val mapper = JsonMapper.builder().build()

  @TestConfiguration(proxyBeanMethods = false)
  class SyntheticMarket {
    @Bean
    @Primary
    fun fixtureMarketClient(mapper: JsonMapper): TwseMarketDataClient =
      object : TwseMarketDataClient(mapper, "https://example.invalid/STOCK_DAY") {
        override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> =
          (0..4).map { index -> DailyBar(LocalDate.of(2024, 1, 1).plusDays(index.toLong()), 100.0) }
      }
  }

  private fun body(initial: String): String =
    """{"symbol":"2330","from":"2024-01-01","to":"2024-01-05","strategy":"ma-crossover","fastWindow":2,"slowWindow":3,"initialCapital":$initial,"monthlyContribution":0,"commissionRate":0.001425,"sellTaxRate":0.003,"useMarketTaxDefaults":false}"""

  private fun post(body: String): HttpResponse<String> = http.send(
    HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/backtests"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build(),
    HttpResponse.BodyHandlers.ofString()
  )

  @Test
  fun contextStartsAndCompatiblePostReturnsExactNumericLedgerAndResearchMetadata() {
    val response = post(body("1000"))
    assertEquals(200, response.statusCode(), response.body())
    val json = mapper.readTree(response.body())
    assertEquals("2330", json.path("symbol").asText())
    assertEquals("LIMITED_RESEARCH", json.path("status").asText())
    assertEquals("BLOCKED", json.path("releaseStatus").asText())
    assertEquals("NEXT_CLOSE_PROXY", json.path("metadata").path("executionModel").asText())
    assertEquals(3, json.path("results").size())
    val dca = json.path("results").get(1)
    assertTrue(dca.path("endingValue").isNumber)
    assertEquals("998.72", dca.path("endingValue").asText())
    assertEquals(9, dca.path("trades").get(0).path("quantity").asInt())
    assertEquals("1.28", dca.path("trades").get(0).path("fee").asText())
    val hold = json.path("results").get(2)
    assertEquals("2330-buy-and-hold", hold.path("key").asText())
    assertEquals("998.72", hold.path("endingValue").asText())
    assertEquals(9, hold.path("dailyEquity").get(0).path("shares").asInt())
    assertTrue(hold.path("annualizedRealizedVolatility").isNumber)
    assertEquals("2024-01-01", dca.path("series").get(0).path("date").asText())
    assertFalse(response.body().contains("Infinity"))
    assertFalse(response.body().contains("NaN"))
  }

  @Test
  fun databaseHealthExecutesAQueryAgainstTheConfiguredDatasource() {
    val response = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/health/database")).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(200, response.statusCode(), response.body())
    val json = mapper.readTree(response.body())
    assertEquals("ok", json.path("status").asText())
    assertEquals("ok", json.path("database").asText())
  }

  @Test
  fun stockHistoryReturnsCloseOnlyBarsAndExplicitResearchLimitations() {
    val response = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/stocks/2330/history?from=2024-01-01&to=2024-01-05")).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(200, response.statusCode(), response.body())
    val json = mapper.readTree(response.body())
    assertEquals("2330", json.path("symbol").asText())
    assertEquals("UNKNOWN", json.path("licensingStatus").asText())
    assertEquals("2024-01-01", json.path("observedFrom").asText())
    assertEquals("2024-01-05", json.path("observedTo").asText())
    assertEquals(5, json.path("bars").size())
    assertTrue(json.path("bars").get(0).has("close"))
    assertFalse(json.path("bars").get(0).has("open"))
    assertTrue(json.path("limitations").toString().contains("非即時") || json.path("limitations").toString().contains("授權"))
  }

  @Test
  fun stockHistoryAcceptsFiveYearWindowAndRejectsLongerRange() {
    val allowed = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/stocks/2330/history?from=2021-01-01&to=2026-01-01")).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(200, allowed.statusCode(), allowed.body())
    val rejected = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/v1/stocks/2330/history?from=2020-12-31&to=2026-01-01")).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(400, rejected.statusCode(), rejected.body())
  }

  @Test
  fun localProfileCreatesReplayTablesAndPersistsDecisionWithoutChangingApiShape() {
    assertEquals(
      2,
      jdbc.queryForObject(
        "select count(*) from information_schema.tables where table_schema = 'PUBLIC' and table_name in ('REPLAY_SESSION', 'REPLAY_DECISION')",
        Int::class.javaObjectType
      )
    )
    val created = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/replay/sessions"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(200, created.statusCode(), created.body())
    val start = mapper.readTree(created.body())
    val id = start.path("sessionId").asText()
    assertTrue(start.path("observation").isObject)
    val decision = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/replay/sessions/$id/decisions"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("""{"action":"BUY","shares":2}"""))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(200, decision.statusCode(), decision.body())
    assertEquals(1, mapper.readTree(decision.body()).path("events").size())
    val resumed = http.send(
      HttpRequest.newBuilder(URI.create("http://localhost:$port/api/replay/sessions/$id")).GET().build(),
      HttpResponse.BodyHandlers.ofString()
    )
    assertEquals(200, resumed.statusCode(), resumed.body())
    assertEquals(1, mapper.readTree(resumed.body()).path("events").size())
    assertEquals(1, jdbc.queryForObject("select count(*) from replay_session where id = ?", Int::class.javaObjectType, id))
    assertEquals(1, jdbc.queryForObject("select count(*) from replay_decision where session_id = ?", Int::class.javaObjectType, id))
  }

  @Test
  fun httpNullMoneyAndMalformedJsonReturnCodeAndOriginalErrorString() {
    listOf(body("null"), "{invalid").forEach { invalid ->
      val response = post(invalid)
      assertEquals(400, response.statusCode())
      val json = mapper.readTree(response.body())
      assertEquals("INVALID_INPUT", json.path("code").asText())
      assertTrue(json.path("error").isTextual)
    }
  }
}
