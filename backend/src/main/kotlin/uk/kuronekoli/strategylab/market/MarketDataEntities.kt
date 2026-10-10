package uk.kuronekoli.strategylab.market

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "market_data_source")
open class MarketDataSourceEntity(
    @field:Id @field:Column(name = "source_id", length = 80, nullable = false) var sourceId: String = "",
    @field:Column(nullable = false, length = 100) var provider: String = "",
    @field:Column(name = "dataset_title", nullable = false, length = 160) var datasetTitle: String = "",
    @field:Column(name = "dataset_url", nullable = false, length = 400) var datasetUrl: String = "",
    @field:Column(name = "resource_url", nullable = false, length = 400) var resourceUrl: String = "",
    @field:Column(nullable = false, length = 120) var license: String = "",
    @field:Column(name = "license_url", nullable = false, length = 400) var licenseUrl: String = "",
    @field:Column(nullable = false, length = 300) var attribution: String = "",
    @field:Column(name = "last_record_date") var lastRecordDate: LocalDate? = null,
    @field:Column(name = "last_fetched_at") var lastFetchedAt: Instant? = null,
) {
    constructor(metadata: MarketDataSourceMetadata, sourceAsOf: LocalDate, fetchedAt: Instant) : this(
        metadata.sourceId, metadata.provider, metadata.datasetTitle, metadata.datasetUrl, metadata.resourceUrl,
        metadata.license, metadata.licenseUrl, metadata.attribution, sourceAsOf, fetchedAt,
    )

    fun toMetadata() = MarketDataSourceMetadata(sourceId, provider, datasetTitle, datasetUrl, resourceUrl, license, licenseUrl, attribution)
}

@Entity
@Table(
    name = "daily_market_bar",
    uniqueConstraints = [UniqueConstraint(name = "uk_market_bar_symbol_date_adjustment", columnNames = ["symbol", "trading_date", "adjustment_policy"])],
    indexes = [Index(name = "idx_market_bar_symbol_date_adjustment", columnList = "symbol,trading_date,adjustment_policy")],
)
open class DailyMarketBarEntity(
    @field:Id @field:GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @field:Column(nullable = false, length = 12) var symbol: String = "",
    @field:Column(name = "name_at_source", nullable = false, length = 200) var nameAtSource: String = "",
    @field:Column(name = "trading_date", nullable = false) var tradingDate: LocalDate = LocalDate.of(1970, 1, 1),
    @field:Column(nullable = false, precision = 20, scale = 8) var open: BigDecimal = BigDecimal.ZERO,
    @field:Column(nullable = false, precision = 20, scale = 8) var high: BigDecimal = BigDecimal.ZERO,
    @field:Column(nullable = false, precision = 20, scale = 8) var low: BigDecimal = BigDecimal.ZERO,
    @field:Column(nullable = false, precision = 20, scale = 8) var close: BigDecimal = BigDecimal.ZERO,
    @field:Column(nullable = false) var volume: Long = 0,
    @field:Column(name = "adjustment_policy", nullable = false, length = 24) var adjustmentPolicy: String = "RAW",
    @field:Column(name = "source_id", nullable = false, length = 80) var sourceId: String = "",
    @field:Column(name = "source_as_of", nullable = false) var sourceAsOf: LocalDate = LocalDate.of(1970, 1, 1),
    @field:Column(name = "first_fetched_at", nullable = false) var firstFetchedAt: Instant = Instant.EPOCH,
    @field:Column(name = "last_verified_at", nullable = false) var lastVerifiedAt: Instant = Instant.EPOCH,
    @field:Column(name = "quality_status", nullable = false, length = 16) var qualityStatus: String = "VALID",
) {
    constructor(bar: SourceMarketBar, sourceId: String, sourceAsOf: LocalDate, fetchedAt: Instant) : this(
        symbol = bar.symbol, nameAtSource = bar.name, tradingDate = bar.date,
        open = bar.open, high = bar.high, low = bar.low, close = bar.close, volume = bar.volume,
        sourceId = sourceId, sourceAsOf = sourceAsOf, firstFetchedAt = fetchedAt, lastVerifiedAt = fetchedAt,
    )

    fun replaceFrom(bar: SourceMarketBar, snapshot: MarketDataSnapshot) {
        nameAtSource = bar.name; open = bar.open; high = bar.high; low = bar.low; close = bar.close; volume = bar.volume
        sourceId = snapshot.metadata.sourceId; sourceAsOf = snapshot.sourceAsOf; lastVerifiedAt = snapshot.fetchedAt; qualityStatus = "VALID"
    }

    fun toDailyBar() = DailyBar(tradingDate, close, open, high, low, volume)
}

@Entity
@Table(name = "market_data_ingestion_run", indexes = [Index(name = "idx_market_ingest_source_started", columnList = "source_id,started_at")])
open class MarketDataIngestionRunEntity(
    @field:Id @field:Column(name = "run_id", length = 36, nullable = false) var runId: String = "",
    @field:Column(name = "source_id", nullable = false, length = 80) var sourceId: String = "",
    @field:Column(name = "started_at", nullable = false) var startedAt: Instant = Instant.EPOCH,
    @field:Column(name = "finished_at") var finishedAt: Instant? = null,
    @field:Column(name = "source_as_of") var sourceAsOf: LocalDate? = null,
    @field:Column(nullable = false, length = 16) var status: String = "RUNNING",
    @field:Column(name = "rows_received", nullable = false) var rowsReceived: Int = 0,
    @field:Column(name = "rows_inserted", nullable = false) var rowsInserted: Int = 0,
    @field:Column(name = "rows_updated", nullable = false) var rowsUpdated: Int = 0,
    @field:Column(name = "rows_rejected", nullable = false) var rowsRejected: Int = 0,
    @field:Column(name = "error_code", length = 48) var errorCode: String? = null,
)
