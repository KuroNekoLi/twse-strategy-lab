package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class MarketDataSourceMetadata(
    val sourceId: String,
    val provider: String,
    val datasetTitle: String,
    val datasetUrl: String,
    val resourceUrl: String,
    val license: String,
    val licenseUrl: String,
    val attribution: String,
    val licensingStatus: String = "UNKNOWN",
)

data class SourceMarketBar(
    val symbol: String,
    val name: String,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
) {
    fun toDailyBar() = DailyBar(date, close, open, high, low, volume)
}

data class MarketDataSnapshot(
    val metadata: MarketDataSourceMetadata,
    val sourceAsOf: LocalDate,
    val fetchedAt: Instant,
    val bars: List<SourceMarketBar>,
    val rowsRejected: Int = 0,
)

interface DailyMarketDataSource {
    fun fetchLatestSnapshot(): MarketDataSnapshot
}

data class HistoricalMarketDataImport(
    val metadata: MarketDataSourceMetadata,
    val symbol: String,
    val from: LocalDate,
    val to: LocalDate,
    val fetchedAt: Instant,
    val bars: List<SourceMarketBar>,
    val rowsRejected: Int = 0,
)

interface HistoricalMarketDataSource {
    val metadata: MarketDataSourceMetadata
    fun fetchHistory(symbol: String, from: LocalDate, to: LocalDate): HistoricalMarketDataImport
}

data class StoredMarketBars(
    val symbol: String,
    val bars: List<DailyBar>,
    val metadata: MarketDataSourceMetadata,
    val fetchedAt: Instant,
    val lastVerifiedAt: Instant,
    val earliestAvailableDate: LocalDate,
)
