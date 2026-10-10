package uk.kuronekoli.strategylab.market

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Repository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.TransactionDefinition

@Repository
class JpaMarketDataStore(
    private val bars: DailyMarketBarRepository,
    private val sources: MarketDataSourceRepository,
    private val runs: MarketDataIngestionRunRepository,
    private val coverage: HistoricalMarketDataCoverageRepository,
    transactionManager: PlatformTransactionManager,
) : MarketDataStore {
    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }
    @Transactional(readOnly = true)
    override fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars? {
        val rows = bars.findAllBySymbolAndAdjustmentPolicyAndTradingDateBetweenOrderByTradingDateAsc(symbol, "RAW", from, to)
        if (rows.isEmpty()) return null
        val newest = rows.maxBy { it.lastVerifiedAt }
        val sourceMetadata = rows.map { it.sourceId }.distinct().map { id -> sources.findById(id).orElse(null)?.toMetadata() ?: return null }
        val latestMetadata = if (sourceMetadata.all { it.licensingStatus == "CONFIRMED" }) {
            sourceMetadata.firstOrNull { it.sourceId == newest.sourceId }
        } else {
            sourceMetadata.firstOrNull { it.licensingStatus != "CONFIRMED" }
        } ?: return null
        val metadata = latestMetadata.copy(
            licensingStatus = if (sourceMetadata.all { it.licensingStatus == "CONFIRMED" }) "CONFIRMED" else "UNVERIFIED",
            attribution = sourceMetadata.map(MarketDataSourceMetadata::attribution).distinct().joinToString("；"),
            datasetTitle = sourceMetadata.map(MarketDataSourceMetadata::datasetTitle).distinct().joinToString(" + "),
        )
        return StoredMarketBars(
            symbol, rows.map(DailyMarketBarEntity::toDailyBar), metadata,
            rows.maxOf { it.lastVerifiedAt }, rows.maxOf { it.lastVerifiedAt },
            findEarliestDate(symbol) ?: rows.first().tradingDate,
        )
    }

    @Transactional(readOnly = true)
    override fun findEarliestDate(symbol: String): LocalDate? = bars.findFirstBySymbolAndAdjustmentPolicyOrderByTradingDateAsc(symbol, "RAW")?.tradingDate

    @Transactional(readOnly = true)
    override fun findHistoricalCoverage(symbol: String, from: LocalDate, to: LocalDate): List<HistoricalCoverage> =
        coverage.findAllBySymbolAndCoveredFromLessThanEqualAndCoveredToGreaterThanEqualOrderByCoveredFromAsc(symbol, to, from)
            .map { HistoricalCoverage(it.symbol, it.coveredFrom, it.coveredTo, it.verifiedAt) }

    @Transactional
    override fun saveSnapshot(snapshot: MarketDataSnapshot): IngestionResult {
        require(snapshot.bars.isNotEmpty()) { "Cannot persist an empty snapshot" }
        val startedAt = Instant.now()
        val existing = bars.findAllByTradingDateAndAdjustmentPolicy(snapshot.sourceAsOf, "RAW").associateBy { it.symbol }
        var inserted = 0
        var updated = 0
        var skippedSourceConflict = 0
        val entities = snapshot.bars.mapNotNull { sourceBar ->
            val current = existing[sourceBar.symbol]
            if (current == null) {
                inserted++
                DailyMarketBarEntity(sourceBar, snapshot.metadata.sourceId, snapshot.sourceAsOf, snapshot.fetchedAt)
            } else {
                if (current.sourceId != snapshot.metadata.sourceId) {
                    skippedSourceConflict++
                    null
                } else {
                    updated++
                    current.replaceFrom(sourceBar, snapshot)
                    current
                }
            }
        }
        if (entities.isNotEmpty()) {
            sources.save(MarketDataSourceEntity(snapshot.metadata, snapshot.sourceAsOf, snapshot.fetchedAt))
            bars.saveAll(entities)
        }
        runs.save(MarketDataIngestionRunEntity(
            runId = UUID.randomUUID().toString(), sourceId = snapshot.metadata.sourceId,
            startedAt = startedAt, finishedAt = snapshot.fetchedAt, sourceAsOf = snapshot.sourceAsOf,
            status = "SUCCEEDED", rowsReceived = snapshot.bars.size + snapshot.rowsRejected, rowsInserted = inserted, rowsUpdated = updated, rowsRejected = snapshot.rowsRejected,
        ))
        return IngestionResult(inserted, updated, snapshot.rowsRejected, skippedSourceConflict)
    }

    override fun saveHistory(import: HistoricalMarketDataImport): IngestionResult {
        var retry = 0
        while (true) {
            try {
                return writeTransaction.execute { saveHistoryWithinTransaction(import) }
                    ?: error("Historical market data transaction returned no result")
            } catch (error: DataIntegrityViolationException) {
                if (retry++ >= 1) throw error
                // Another app instance may have inserted the same symbol/date after our read.
                // A fresh transaction re-reads those rows and applies the idempotent update path.
            }
        }
    }

    private fun saveHistoryWithinTransaction(import: HistoricalMarketDataImport): IngestionResult {
        require(import.bars.isNotEmpty()) { "Cannot persist empty historical import" }
        require(import.bars.all { it.symbol == import.symbol && it.date in import.from..import.to }) { "Historical import contains bars outside its requested symbol/date range" }
        val existing = bars.findAllBySymbolAndAdjustmentPolicyAndTradingDateBetweenOrderByTradingDateAsc(import.symbol, "RAW", import.from, import.to).associateBy { it.tradingDate }
        var inserted = 0
        var updated = 0
        val entities = import.bars.map { sourceBar ->
            val current = existing[sourceBar.date]
            if (current == null) {
                inserted++
                DailyMarketBarEntity(sourceBar, import.metadata.sourceId, sourceBar.date, import.fetchedAt)
            } else {
                updated++
                current.nameAtSource = sourceBar.name
                current.open = sourceBar.open; current.high = sourceBar.high; current.low = sourceBar.low; current.close = sourceBar.close
                current.volume = sourceBar.volume; current.sourceId = import.metadata.sourceId; current.sourceAsOf = sourceBar.date
                current.lastVerifiedAt = import.fetchedAt; current.qualityStatus = "VALID"
                current
            }
        }
        val latestDataDate = import.bars.maxOf(SourceMarketBar::date)
        val sourceMetadata = sources.findById(import.metadata.sourceId).orElse(null)
            ?.apply {
                provider = import.metadata.provider; datasetTitle = import.metadata.datasetTitle
                datasetUrl = import.metadata.datasetUrl; resourceUrl = import.metadata.resourceUrl
                license = import.metadata.license; licenseUrl = import.metadata.licenseUrl
                attribution = import.metadata.attribution; licensingStatus = import.metadata.licensingStatus
                lastRecordDate = maxOf(lastRecordDate ?: latestDataDate, latestDataDate); lastFetchedAt = import.fetchedAt
            }
            ?: MarketDataSourceEntity(import.metadata, latestDataDate, import.fetchedAt)
        sources.save(sourceMetadata)
        bars.saveAll(entities)
        runs.save(MarketDataIngestionRunEntity(
            runId = UUID.randomUUID().toString(), sourceId = import.metadata.sourceId,
            startedAt = import.fetchedAt, finishedAt = import.fetchedAt, sourceAsOf = latestDataDate,
            status = "SUCCEEDED", rowsReceived = import.bars.size + import.rowsRejected,
            rowsInserted = inserted, rowsUpdated = updated, rowsRejected = import.rowsRejected,
        ))
        coverage.save(HistoricalMarketDataCoverageEntity(
            coverageId = UUID.randomUUID().toString(), symbol = import.symbol,
            coveredFrom = import.from, coveredTo = import.to, verifiedAt = import.fetchedAt,
        ))
        return IngestionResult(inserted, updated, import.rowsRejected)
    }

    @Transactional
    override fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String) {
        runs.save(MarketDataIngestionRunEntity(
            runId = UUID.randomUUID().toString(), sourceId = sourceId, startedAt = startedAt,
            finishedAt = finishedAt, status = "FAILED", errorCode = errorCode.take(48),
        ))
    }
}
