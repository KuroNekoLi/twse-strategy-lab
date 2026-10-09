package uk.kuronekoli.strategylab.market

import java.time.YearMonth

/** Replaceable boundary for licensed historical sources; live quotes are a separate product contract. */
interface HistoricalMarketDataProvider {
    val sourceName: String
    fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar>
}
