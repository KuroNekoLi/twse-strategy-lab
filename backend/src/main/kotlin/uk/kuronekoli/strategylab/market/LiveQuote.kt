package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant

data class LiveQuote(
    val symbol: String,
    val price: BigDecimal,
    val size: Long,
    val volume: Long,
    val eventTime: Instant,
    val receivedAt: Instant,
    val source: String = "Fugle",
    val freshness: String = "LIVE",
)

interface LiveQuoteStreamProvider {
    fun subscribe(symbol: String, listener: Listener): AutoCloseable

    interface Listener {
        fun onQuote(quote: LiveQuote)
        fun onError(code: String)
    }
}
