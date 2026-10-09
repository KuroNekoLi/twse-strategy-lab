package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.LocalDate

/** Captured close only: no OHLC, dividend or corporate-action data is implied. */
data class DailyBar(val date: LocalDate, val close: BigDecimal) {
    constructor(date: LocalDate, close: Double) : this(date, decimal(close))
    companion object {
        private fun decimal(value: Double): BigDecimal {
            require(value.isFinite()) { "收盤價必須為有限數值。" }
            return BigDecimal.valueOf(value)
        }
    }
}
