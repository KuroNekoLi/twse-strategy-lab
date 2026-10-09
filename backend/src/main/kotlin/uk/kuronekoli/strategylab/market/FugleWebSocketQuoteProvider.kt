package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletionStage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Component
class FugleWebSocketQuoteProvider(
    private val mapper: JsonMapper,
    @Value("\${FUGLE_WS_URL:wss://api.fugle.tw/marketdata/v1.0/stock/streaming}") private val endpoint: String,
    @Value("\${FUGLE_API_KEY:}") private val apiKey: String,
) : LiveQuoteStreamProvider {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override fun subscribe(symbol: String, listener: LiveQuoteStreamProvider.Listener): AutoCloseable {
        if (apiKey.isBlank() || endpoint.isBlank() || !isOfficialSecureEndpoint()) {
            listener.onError("LIVE_PROVIDER_UNAVAILABLE")
            return AutoCloseable {}
        }
        val closed = AtomicBoolean(false)
        val authenticated = AtomicBoolean(false)
        val socketFuture = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10))
            .buildAsync(URI.create(endpoint), object : WebSocket.Listener {
                private val fragments = StringBuilder()

                override fun onOpen(webSocket: WebSocket) {
                    webSocket.request(1)
                    send(webSocket, "auth", mapper.createObjectNode().put("apikey", apiKey))
                }

                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    fragments.append(data)
                    if (last) {
                        val raw = fragments.toString()
                        fragments.setLength(0)
                        handle(raw, webSocket, symbol, listener, authenticated)
                    }
                    webSocket.request(1)
                    return null
                }

                override fun onError(webSocket: WebSocket, error: Throwable) {
                    if (closed.compareAndSet(false, true)) {
                        log.warn("Fugle WebSocket failed: exceptionType={}", error.javaClass.name)
                        listener.onError("LIVE_PROVIDER_UNAVAILABLE")
                    }
                }

                override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
                    if (closed.compareAndSet(false, true)) listener.onError("LIVE_PROVIDER_DISCONNECTED")
                    return null
                }
            })
        socketFuture.whenComplete { _, error ->
            if (error != null && closed.compareAndSet(false, true)) {
                log.warn("Fugle WebSocket connection failed: exceptionType={}", error.javaClass.name)
                listener.onError("LIVE_PROVIDER_UNAVAILABLE")
            }
        }
        CompletableFuture.delayedExecutor(15, java.util.concurrent.TimeUnit.SECONDS).execute {
            if (!authenticated.get() && closed.compareAndSet(false, true)) {
                listener.onError("LIVE_PROVIDER_AUTH_TIMEOUT")
                socketFuture.thenAccept { socket -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "authentication timeout") }
            }
        }
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) socketFuture.thenAccept { socket -> socket.sendClose(WebSocket.NORMAL_CLOSURE, "client disconnected") }
        }
    }

    private fun handle(raw: String, socket: WebSocket, symbol: String, listener: LiveQuoteStreamProvider.Listener, authenticated: AtomicBoolean) {
        try {
            val root = mapper.readTree(raw) ?: return
            when (root.path("event").asText()) {
                "authenticated" -> if (authenticated.compareAndSet(false, true)) send(socket, "subscribe", mapper.createObjectNode().put("channel", "trades").put("symbol", symbol))
                "error" -> listener.onError("LIVE_PROVIDER_REJECTED")
                "data" -> if (root.path("channel").asText() == "trades") parseTrade(root.path("data"), symbol)?.let(listener::onQuote)
            }
        } catch (error: Exception) {
            log.warn("Fugle message rejected: exceptionType={}", error.javaClass.name)
            listener.onError("LIVE_PROVIDER_INVALID_MESSAGE")
        }
    }

    private fun send(socket: WebSocket, event: String, data: JsonNode) {
        socket.sendText(mapper.createObjectNode().put("event", event).set("data", data).toString(), true)
    }

    private fun isOfficialSecureEndpoint(): Boolean = try {
        val uri = URI.create(endpoint)
        uri.scheme.equals("wss", ignoreCase = true) && uri.host.equals("api.fugle.tw", ignoreCase = true)
    } catch (_: IllegalArgumentException) { false }

    internal fun parseTrade(data: JsonNode, expectedSymbol: String, receivedAt: Instant = Instant.now()): LiveQuote? {
        if (!data.isObject || data.path("symbol").asText() != expectedSymbol) return null
        val price = data.path("price")
        val size = data.path("size")
        val volume = data.path("volume")
        val time = data.path("time")
        if (!price.isNumber || !size.canConvertToLong() || !volume.canConvertToLong() || !time.canConvertToLong()) return null
        val priceValue = price.decimalValue()
        val sizeValue = size.longValue()
        val volumeValue = volume.longValue()
        val micros = time.longValue()
        if (priceValue.signum() <= 0 || sizeValue < 0 || volumeValue < 0 || micros <= 0) return null
        return LiveQuote(expectedSymbol, priceValue, sizeValue, volumeValue, Instant.ofEpochSecond(micros / 1_000_000, (micros % 1_000_000) * 1_000), receivedAt)
    }
}
