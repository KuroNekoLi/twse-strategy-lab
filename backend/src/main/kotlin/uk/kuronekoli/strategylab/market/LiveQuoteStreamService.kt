package uk.kuronekoli.strategylab.market

import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import uk.kuronekoli.strategylab.api.BacktestException

@Service
class LiveQuoteStreamService(
    private val provider: LiveQuoteStreamProvider,
    private val environment: Environment,
    @Value("\${app.live-market-data.enabled:false}") private val enabled: Boolean,
    @Value("\${FUGLE_API_KEY:}") private val apiKey: String,
) {
    fun stream(symbol: String): SseEmitter {
        if (!SYMBOL.matcher(symbol).matches()) return failedStream("INVALID_SYMBOL")
        if (!environment.acceptsProfiles(Profiles.of("local")) || !enabled || apiKey.isBlank()) {
            return failedStream("LIVE_MARKET_DATA_UNAVAILABLE")
        }
        val emitter = SseEmitter(0L)
        val handle = AtomicReference<AutoCloseable?>()
        emitter.onCompletion { handle.get()?.close() }
        emitter.onTimeout { handle.get()?.close() }
        try {
            emitter.send(SseEmitter.event().name("status").data(StreamStatus("Fugle", "CONNECTING"), MediaType.APPLICATION_JSON))
            val subscription = provider.subscribe(symbol, object : LiveQuoteStreamProvider.Listener {
                override fun onQuote(quote: LiveQuote) {
                    try { emitter.send(SseEmitter.event().name("quote").id(quote.eventTime.toString()).data(quote, MediaType.APPLICATION_JSON)) }
                    catch (_: Exception) { handle.get()?.close(); emitter.complete() }
                }

                override fun onError(code: String) {
                    try { emitter.send(SseEmitter.event().name("stream-error").data(StreamError(code), MediaType.APPLICATION_JSON)) }
                    catch (_: Exception) { }
                    emitter.complete()
                }
            })
            handle.set(subscription)
            return emitter
        } catch (error: BacktestException) {
            throw error
        } catch (_: Exception) {
            handle.get()?.close()
            return failedStream("LIVE_MARKET_DATA_UNAVAILABLE")
        }
    }

    private fun failedStream(code: String): SseEmitter = SseEmitter(0L).also { emitter ->
        try {
            emitter.send(SseEmitter.event().name("stream-error").data(StreamError(code), MediaType.APPLICATION_JSON))
        } finally {
            emitter.complete()
        }
    }

    data class StreamStatus(val source: String, val status: String)
    data class StreamError(val code: String)

    companion object { private val SYMBOL = Pattern.compile("\\d{4,6}") }
}
