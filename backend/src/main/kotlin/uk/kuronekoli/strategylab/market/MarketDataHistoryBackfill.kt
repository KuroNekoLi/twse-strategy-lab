package uk.kuronekoli.strategylab.market

import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.backtest.BacktestValidation

@Service
class MarketDataHistoryBackfillService(
    private val source: HistoricalMarketDataSource,
    private val store: MarketDataStore,
) {
    fun import(symbol: String, from: LocalDate, to: LocalDate, minimumRows: Int = 0): IngestionResult {
        require(minimumRows >= 0) { "minimumRows must not be negative" }
        val imported = source.fetchHistory(symbol, from, to)
        if (imported.bars.isEmpty()) error("歷史行情來源沒有回傳有效的日 K；匯入取消。")
        if (imported.bars.size < minimumRows) {
            throw BacktestException("DATA_COVERAGE_INSUFFICIENT", "歷史行情筆數明顯不足；資料未寫入。", 502)
        }
        BacktestValidation.bars(imported.bars.map(SourceMarketBar::toDailyBar))
        return store.saveHistory(imported)
    }

}

@Component
@ConditionalOnProperty(name = ["app.market-data.historical-backfill-enabled"], havingValue = "true")
class HistoricalMarketDataBackfillRunner(
    private val backfill: MarketDataHistoryBackfillService,
    @Value("\${app.market-data.historical-backfill-symbol:0050}") private val symbol: String,
    @Value("\${app.market-data.historical-backfill-from:2010-01-01}") private val from: LocalDate,
    @Value("\${app.market-data.historical-backfill-to:today}") private val toText: String,
    @Value("\${app.market-data.historical-backfill-minimum-rows:3500}") private val minimumRows: Int,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val to = if (toText == "today") LocalDate.now() else LocalDate.parse(toText)
        try {
            val result = backfill.import(symbol, from, to, minimumRows)
            log.info("Historical market data imported: symbol={}, from={}, to={}, inserted={}, updated={}, rejected={}", symbol, from, to, result.inserted, result.updated, result.rejected)
        } catch (error: Exception) {
            val code = (error as? BacktestException)?.code ?: "UPSTREAM_DATA_UNAVAILABLE"
            val safeReason = (error as? BacktestException)?.message?.take(160) ?: error.javaClass.simpleName
            log.error("Historical market data backfill failed; application startup will continue: symbol={}, from={}, to={}, code={}, reason={}", symbol, from, to, code, safeReason)
        }
    }

    companion object { private val log = LoggerFactory.getLogger(HistoricalMarketDataBackfillRunner::class.java) }
}
