package uk.kuronekoli.strategylab.market

import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.backtest.BacktestValidation

@Service
class MarketDataIngestionService(
    private val source: DailyMarketDataSource,
    private val store: MarketDataStore,
    @Value("\${app.market-data.ingestion-enabled:false}") private val enabled: Boolean,
) {
    private val inFlight = AtomicBoolean(false)
    private val clock: Clock = Clock.systemUTC()

    fun refreshLatest(): IngestionResult? {
        if (!enabled || !inFlight.compareAndSet(false, true)) return null
        val startedAt = clock.instant()
        try {
            val snapshot = source.fetchLatestSnapshot()
            if (snapshot.bars.isEmpty() || snapshot.bars.any { it.date != snapshot.sourceAsOf }) {
                throw BacktestException("DATA_INTEGRITY_FAILED", "行情快照日期不一致；本次資料未寫入。", 502)
            }
            snapshot.bars.groupBy(SourceMarketBar::symbol).values.forEach { rows ->
                BacktestValidation.bars(rows.map(SourceMarketBar::toDailyBar))
            }
            val result = store.saveSnapshot(snapshot)
            log.info("Open market data snapshot stored: source={}, date={}, received={}, inserted={}, updated={}, rejectedNoOhlc={}, skippedSourceConflict={}", snapshot.metadata.sourceId, snapshot.sourceAsOf, snapshot.bars.size + snapshot.rowsRejected, result.inserted, result.updated, result.rejected, result.skippedSourceConflict)
            return result
        } catch (error: Exception) {
            val code = (error as? BacktestException)?.code ?: "UPSTREAM_DATA_UNAVAILABLE"
            runCatching { store.recordFailure(GovernmentOpenDataDailySource.METADATA.sourceId, startedAt, clock.instant(), code) }
            log.warn("Open market data refresh failed: code={}", code)
            throw error
        } finally {
            inFlight.set(false)
        }
    }

    companion object { private val log = LoggerFactory.getLogger(MarketDataIngestionService::class.java) }
}
