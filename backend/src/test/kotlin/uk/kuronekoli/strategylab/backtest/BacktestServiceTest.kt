package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.api.ApiExceptionHandler.NoMarketDataException
import uk.kuronekoli.strategylab.market.DailyBar
import uk.kuronekoli.strategylab.market.HistoricalMarketDataProvider

class BacktestServiceTest {
  private val clock = Clock.fixed(Instant.parse("2024-01-20T04:00:00Z"), ZoneId.of("Asia/Taipei"))

  private class CapturedClient(private val bars: List<DailyBar>) : HistoricalMarketDataProvider {
    override val sourceName = "fixture historical source"
    var requestedFirst: YearMonth? = null
    var requestedLast: YearMonth? = null

    override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> {
      requestedFirst = first
      requestedLast = last
      return bars
    }
  }

  @Test
  fun coverageLimitationsAndResolvedFingerprintAreExplicitAndDeterministic() {
    val client = CapturedClient(BacktestEngineGoldenTest().prices(100.0, 100.0, 100.0, 120.0, 110.0))
    val service = BacktestService(client, clock)
    val request = BacktestEngineGoldenTest().request("ma-crossover", "1000", "0", "0.001425", "0.003")
    val report = service.run(request)
    assertEquals("2024-12-31", report.requestedTo)
    assertEquals("2024-01-05", report.to)
    assertEquals("2024-01-20", report.metadata.resolvedConfig.effectiveTo)
    assertEquals(YearMonth.of(2024, 1), client.requestedLast)
    assertEquals("LIMITED_RESEARCH", report.status)
    assertEquals("BLOCKED", report.releaseStatus)
    assertEquals("fixture historical source", report.dataSource)
    assertEquals("RAW_CLOSE_UNADJUSTED_V1", report.metadata.priceAdjustmentPolicy)
    assertEquals("NEXT_CLOSE_PROXY", report.metadata.executionModel)
    assertEquals("IDENTIFIED_NOT_ARCHIVED", report.metadata.reproducibilityStatus)
    assertEquals("UNKNOWN", report.assets[0].marketStatus)
    assertEquals("UNVERIFIED_CALENDAR", report.assets[0].coverageStatus)
    val codes = report.limitations.map { it.code }
    for (expected in listOf("DIVIDENDS_UNSUPPORTED", "CORPORATE_ACTIONS_UNSUPPORTED", "CALENDAR_STATUS_UNKNOWN", "LICENSING_UNVERIFIED", "FUTURE_END_CLAMPED", "COVERAGE_DIFFERS_FROM_REQUEST", "SNAPSHOT_NOT_ARCHIVED")) assertTrue(codes.contains(expected), expected)
    assertEquals(report, service.run(request))
    assertEquals(64, report.metadata.datasetHash.length)
    assertTrue(report.metadata.backtestId.startsWith("bt_"))
  }

  @Test
  fun equivalentDecimalRepresentationHasSameDatasetFingerprintButPriceChangeDoesNot() {
    val helper = BacktestEngineGoldenTest()
    val first = helper.prices(100.0, 100.0, 100.0, 120.0, 110.0)
    val scaled = first.map { DailyBar(it.date, it.close.setScale(8)) }
    assertEquals(BacktestFingerprint.dataset("2330", first), BacktestFingerprint.dataset("2330", scaled))
    assertNotEquals(BacktestFingerprint.dataset("2330", first), BacktestFingerprint.dataset("2330", helper.prices(100.0, 100.0, 100.0, 120.0, 111.0)))
  }

  @Test
  fun parameterAndDatasetChangesChangeRunIdentity() {
    val helper = BacktestEngineGoldenTest()
    val base = BacktestService(CapturedClient(helper.prices(100.0, 100.0, 100.0, 120.0, 110.0)), clock)
    val changedData = BacktestService(CapturedClient(helper.prices(100.0, 100.0, 100.0, 120.0, 111.0)), clock)
    val request = helper.request("ma-crossover", "1000", "0", "0", "0")
    val id = base.run(request).metadata.backtestId
    assertNotEquals(id, changedData.run(request).metadata.backtestId)
    assertNotEquals(id, base.run(helper.request("ma-crossover", "1001", "0", "0", "0")).metadata.backtestId)
  }

  @Test
  fun invalidNullNegativeNonfiniteAndExcessiveInputsHaveStableCodes() {
    val helper = BacktestEngineGoldenTest()
    val service = BacktestService(CapturedClient(helper.prices(100.0, 100.0, 100.0, 120.0, 110.0)), clock)
    assertEquals("INVALID_INPUT", assertThrows(BacktestException::class.java) { service.run(null) }.code)
    val requests = listOf(
      helper.request("unknown", "1000", "0", "0", "0"),
      helper.request("ma-crossover", "-1", "0", "0", "0"),
      helper.request("ma-crossover", "1000", "-1", "0", "0"),
      helper.request("ma-crossover", "1000.001", "0", "0", "0"),
      helper.request("ma-crossover", "1000000000001", "0", "0", "0"),
      helper.request("ma-crossover", "1000", "0", "0.7", "0.3"),
      helper.request("ma-crossover", "1000", "0", "1", "0"),
      helper.request("ma-crossover", "1000", "0", "0.0014249999999999998", "0")
    )
    for (request in requests) assertEquals("INVALID_INPUT", assertThrows(BacktestException::class.java) { service.run(request) }.code)
    val invalid = BacktestRequest(null, listOf("2330"), null, null, "ma-crossover", 2, 3, 2, Double.NaN, 55.0, 3, 0.5, 2, 20.0, 20.0, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false)
    assertEquals("INVALID_INPUT", assertThrows(BacktestException::class.java) { service.run(invalid) }.code)
    val nullSymbols = BacktestRequest("2330", listOf("2330", null), "2024-01-01", "2024-01-10", "ma-crossover", 2, 3, null, null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, false)
    assertEquals("INVALID_INPUT", assertThrows(BacktestException::class.java) { service.run(nullSymbols) }.code)
  }

  @Test
  fun noHardcoded0050RetroactiveSplitAdjustmentRemains() {
    val helper = BacktestEngineGoldenTest()
    val service = BacktestService(CapturedClient(helper.prices(100.0, 100.0, 100.0, 100.0, 100.0)), clock)
    val request = BacktestRequest("0050", null, "2024-01-01", "2024-01-05", "ma-crossover", 2, 3, null, null, null, null, null, null, null, null, BigDecimal.ZERO, BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, true)
    val response = service.run(request)
    assertEquals(10, response.results[1].trades[0].quantity)
    assertEquals(0, BigDecimal("0.001").compareTo(response.metadata.resolvedConfig.appliedSellTaxRates["0050"]))
    assertTrue(response.limitations.any { it.code == "TAX_CLASSIFICATION_ESTIMATE" })
  }

  @Test
  fun emptyProviderResultIsNoMarketDataRatherThanMalformedData() {
    val service = BacktestService(CapturedClient(emptyList()), clock)
    val request = BacktestEngineGoldenTest().request("ma-crossover", "1000", "0", "0", "0")
    assertThrows(NoMarketDataException::class.java) { service.run(request) }
  }
}
