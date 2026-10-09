package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.LocalDate

/** Daily market observation. OHLCV may be absent for close-only synthetic/backtest fixtures. */
data class DailyBar(
    val date: LocalDate,
    val close: BigDecimal,
    val open: BigDecimal? = null,
    val high: BigDecimal? = null,
    val low: BigDecimal? = null,
    val volume: Long? = null,
) {
    constructor(date: LocalDate, close: Double) : this(date, decimal(close))

    init {
        if (listOf(open, high, low, volume).any { it != null }) {
            require(open != null && high != null && low != null && volume != null) { "OHLCV 必須完整提供。" }
            require(open.signum() > 0 && high.signum() > 0 && low.signum() > 0) { "OHLC 價格必須大於零。" }
            require(volume >= 0) { "成交量不可為負數。" }
            require(high >= low && high >= open && high >= close && low <= open && low <= close) { "OHLC 價格關係不合理。" }
        }
    }

    val hasOhlcv: Boolean get() = open != null && high != null && low != null && volume != null
    companion object {
        private fun decimal(value: Double): BigDecimal {
            require(value.isFinite()) { "收盤價必須為有限數值。" }
            return BigDecimal.valueOf(value)
        }
    }
}
