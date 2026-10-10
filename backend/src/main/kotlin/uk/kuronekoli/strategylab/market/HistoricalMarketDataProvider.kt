package uk.kuronekoli.strategylab.market

import java.time.YearMonth

/** Replaceable boundary for licensed historical sources; live quotes are a separate product contract. */
interface HistoricalMarketDataProvider {
    val sourceName: String
    fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar>

    fun loadSnapshot(symbol: String, first: YearMonth, last: YearMonth): HistoricalBarsSnapshot {
        val bars = load(symbol, first, last)
        return HistoricalBarsSnapshot(
            bars = bars,
            source = sourceName,
            sourceUrl = null,
            licenseUrl = null,
            licensingStatus = "UNKNOWN",
            adjustmentPolicy = "未調整原始價格",
            fetchedAt = java.time.Instant.now(),
            lastVerifiedAt = null,
            earliestAvailableDate = bars.minOfOrNull { it.date },
            limitations = emptyList(),
        )
    }
}

data class HistoricalBarsSnapshot(
    val bars: List<DailyBar>,
    val source: String,
    val sourceUrl: String?,
    val licenseUrl: String?,
    val licensingStatus: String,
    val adjustmentPolicy: String,
    val fetchedAt: java.time.Instant,
    val lastVerifiedAt: java.time.Instant?,
    val earliestAvailableDate: java.time.LocalDate?,
    val limitations: List<String>,
)
