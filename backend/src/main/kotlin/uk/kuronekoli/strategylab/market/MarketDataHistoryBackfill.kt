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
    val publicDisplayAllowed: Boolean
        get() = source.metadata.licensingStatus == "CONFIRMED"

    fun import(symbol: String, from: LocalDate, to: LocalDate, minimumRows: Int = 0): IngestionResult {
        require(minimumRows >= 0) { "minimumRows must not be negative" }
        if (source.metadata.licensingStatus != "CONFIRMED") {
            throw BacktestException("MARKET_DATA_LICENSE_UNVERIFIED", "行情來源的公開展示權尚未確認；本次查詢未回補或儲存資料。", 503)
        }
        val imported = source.fetchHistory(symbol, from, to)
        if (imported.symbol != symbol || imported.from != from || imported.to != to || imported.metadata.sourceId != source.metadata.sourceId) {
            throw BacktestException("UPSTREAM_DATA_UNAVAILABLE", "行情來源回傳的標的、日期範圍或來源識別不符；資料未寫入。", 502)
        }
        if (imported.metadata.licensingStatus != "CONFIRMED") {
            throw BacktestException("MARKET_DATA_LICENSE_UNVERIFIED", "行情資料的公開展示權尚未確認；本次資料未寫入。", 503)
        }
        if (imported.bars.isEmpty()) throw BacktestException("NO_MARKET_DATA", "$symbol 在此日期範圍沒有可用日 K。", 404)
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
