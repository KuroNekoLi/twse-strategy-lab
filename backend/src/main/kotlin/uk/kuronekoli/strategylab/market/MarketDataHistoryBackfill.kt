package uk.kuronekoli.strategylab.market

import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import uk.kuronekoli.strategylab.backtest.BacktestValidation

@Service
class MarketDataHistoryBackfillService(
    private val source: HistoricalMarketDataSource,
    private val store: MarketDataStore,
) {
    fun import(symbol: String, from: LocalDate, to: LocalDate): IngestionResult {
        val imported = source.fetchHistory(symbol, from, to)
        if (imported.bars.isEmpty()) error("FinMind 沒有回傳有效的日 K；匯入取消。")
        BacktestValidation.bars(imported.bars.map(SourceMarketBar::toDailyBar))
        return store.saveHistory(imported)
    }
}

@Component
@ConditionalOnProperty(name = ["app.market-data.finmind-backfill-enabled"], havingValue = "true")
class FinMindBackfillRunner(
    private val backfill: MarketDataHistoryBackfillService,
    @Value("\${app.market-data.finmind-backfill-symbol:0050}") private val symbol: String,
    @Value("\${app.market-data.finmind-backfill-from:2010-01-01}") private val from: LocalDate,
    @Value("\${app.market-data.finmind-backfill-to:today}") private val toText: String,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        val to = if (toText == "today") LocalDate.now() else LocalDate.parse(toText)
        val result = backfill.import(symbol, from, to)
        log.info("FinMind history imported: symbol={}, from={}, to={}, inserted={}, updated={}, rejected={}", symbol, from, to, result.inserted, result.updated, result.rejected)
    }

    companion object { private val log = LoggerFactory.getLogger(FinMindBackfillRunner::class.java) }
}
