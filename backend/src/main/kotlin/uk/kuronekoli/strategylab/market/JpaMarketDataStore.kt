package uk.kuronekoli.strategylab.market

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JpaMarketDataStore(
    private val bars: DailyMarketBarRepository,
    private val sources: MarketDataSourceRepository,
    private val runs: MarketDataIngestionRunRepository,
) : MarketDataStore {
    @Transactional(readOnly = true)
    override fun findBars(symbol: String, from: LocalDate, to: LocalDate): StoredMarketBars? {
        val rows = bars.findAllBySymbolAndAdjustmentPolicyAndTradingDateBetweenOrderByTradingDateAsc(symbol, "RAW", from, to)
        if (rows.isEmpty()) return null
        val newest = rows.maxBy { it.lastVerifiedAt }
        val metadata = sources.findById(newest.sourceId).orElse(null)?.toMetadata() ?: return null
        return StoredMarketBars(
            symbol, rows.map(DailyMarketBarEntity::toDailyBar), metadata,
            rows.maxOf { it.lastVerifiedAt }, rows.maxOf { it.lastVerifiedAt },
            findEarliestDate(symbol) ?: rows.first().tradingDate,
        )
    }

    @Transactional(readOnly = true)
    override fun findEarliestDate(symbol: String): LocalDate? = bars.findFirstBySymbolAndAdjustmentPolicyOrderByTradingDateAsc(symbol, "RAW")?.tradingDate

    @Transactional
    override fun saveSnapshot(snapshot: MarketDataSnapshot): IngestionResult {
        require(snapshot.bars.isNotEmpty()) { "Cannot persist an empty snapshot" }
        val startedAt = Instant.now()
        val existing = bars.findAllByTradingDateAndAdjustmentPolicy(snapshot.sourceAsOf, "RAW").associateBy { it.symbol }
        var inserted = 0
        var updated = 0
        val entities = snapshot.bars.map { sourceBar ->
            val current = existing[sourceBar.symbol]
            if (current == null) {
                inserted++
                DailyMarketBarEntity(sourceBar, snapshot.metadata.sourceId, snapshot.sourceAsOf, snapshot.fetchedAt)
            } else {
                check(current.sourceId == snapshot.metadata.sourceId) {
                    "Canonical source changed for ${sourceBar.symbol} on ${snapshot.sourceAsOf}; explicit source migration is required"
                }
                updated++
                current.replaceFrom(sourceBar, snapshot)
                current
            }
        }
        sources.save(MarketDataSourceEntity(snapshot.metadata, snapshot.sourceAsOf, snapshot.fetchedAt))
        bars.saveAll(entities)
        runs.save(MarketDataIngestionRunEntity(
            runId = UUID.randomUUID().toString(), sourceId = snapshot.metadata.sourceId,
            startedAt = startedAt, finishedAt = snapshot.fetchedAt, sourceAsOf = snapshot.sourceAsOf,
            status = "SUCCEEDED", rowsReceived = snapshot.bars.size + snapshot.rowsRejected, rowsInserted = inserted, rowsUpdated = updated, rowsRejected = snapshot.rowsRejected,
        ))
        return IngestionResult(inserted, updated, snapshot.rowsRejected)
    }

    @Transactional
    override fun recordFailure(sourceId: String, startedAt: Instant, finishedAt: Instant, errorCode: String) {
        runs.save(MarketDataIngestionRunEntity(
            runId = UUID.randomUUID().toString(), sourceId = sourceId, startedAt = startedAt,
            finishedAt = finishedAt, status = "FAILED", errorCode = errorCode.take(48),
        ))
    }
}
