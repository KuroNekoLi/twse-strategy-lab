package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.Instant
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.api.BacktestException

class LiveQuoteStreamServiceTest {
    private class FakeProvider : LiveQuoteStreamProvider {
        var subscribedSymbol: String? = null
        var listener: LiveQuoteStreamProvider.Listener? = null
        var closed = false
        override fun subscribe(symbol: String, listener: LiveQuoteStreamProvider.Listener): AutoCloseable {
            subscribedSymbol = symbol
            this.listener = listener
            return AutoCloseable { closed = true }
        }
    }

    @Test fun disabledAndMissingKeyReturnClosedSseErrorsWithoutProviderConnection() {
        val env = MockEnvironment().apply { setActiveProfiles("local") }
        for ((enabled, key) in listOf(false to "key", true to "")) {
            val provider = FakeProvider()
            val emitter = LiveQuoteStreamService(provider, env, enabled, key).stream("2330")
            assertTrue(emitter is SseEmitter)
            assertNull(provider.subscribedSymbol)
        }
    }

    @Test fun providerIsUsedOnlyInLocalProfileAndDisconnectClosesSubscription() {
        val env = MockEnvironment()
        val unavailableProvider = FakeProvider()
        val unavailable = LiveQuoteStreamService(unavailableProvider, env, true, "test-key").stream("2330")
        assertTrue(unavailable is SseEmitter)
        assertNull(unavailableProvider.subscribedSymbol)

        env.setActiveProfiles("local")
        val provider = FakeProvider()
        val emitter = LiveQuoteStreamService(provider, env, true, "test-key").stream("2330")
        assertTrue(emitter is SseEmitter)
        assertEquals("2330", provider.subscribedSymbol)
        val quote = LiveQuote("2330", BigDecimal("123.5"), 2, 100, Instant.parse("2026-10-09T01:00:00Z"), Instant.parse("2026-10-09T01:00:01Z"))
        assertEquals("Fugle", quote.source)
        assertEquals("LIVE", quote.freshness)
        assertTrue(quote.eventTime.isBefore(quote.receivedAt))
        provider.listener!!.onQuote(quote)
        provider.listener!!.onError("LIVE_PROVIDER_DISCONNECTED")
        emitter.complete()
    }

    @Test fun fugleMicrosecondTimestampNormalizesAndMalformedTradesAreDropped() {
        val adapter = FugleWebSocketQuoteProvider(JsonMapper.builder().build(), "wss://example.invalid/stream", "")
        val data = JsonMapper.builder().build().readTree("""{"symbol":"2330","price":123.5,"size":2,"volume":100,"time":1791507601123456}""")
        val receivedAt = Instant.parse("2026-10-09T01:00:02Z")
        val quote = adapter.parseTrade(data, "2330", receivedAt)!!
        assertEquals(Instant.ofEpochSecond(1791507601, 123456000), quote.eventTime)
        assertEquals(receivedAt, quote.receivedAt)
        assertEquals("Fugle", quote.source)
        assertEquals("LIVE", quote.freshness)
        assertNull(adapter.parseTrade(data, "0050", receivedAt))
        assertNull(adapter.parseTrade(JsonMapper.builder().build().readTree("""{"symbol":"2330","price":0,"size":2,"volume":1,"time":1}"""), "2330", receivedAt))
    }

    @Test fun providerWithMissingCredentialsFailsWithoutOpeningAConnection() {
        val provider = FugleWebSocketQuoteProvider(JsonMapper.builder().build(), "", "")
        var error: String? = null
        val close = provider.subscribe("2330", object : LiveQuoteStreamProvider.Listener {
            override fun onQuote(quote: LiveQuote) = Unit
            override fun onError(code: String) { error = code }
        })
        assertEquals("LIVE_PROVIDER_UNAVAILABLE", error)
        close.close()
    }

    @Test fun providerRefusesToSendCredentialToNonFugleOrInsecureEndpoint() {
        val provider = FugleWebSocketQuoteProvider(JsonMapper.builder().build(), "ws://attacker.invalid/stream", "secret-test-value")
        var error: String? = null
        val close = provider.subscribe("2330", object : LiveQuoteStreamProvider.Listener {
            override fun onQuote(quote: LiveQuote) = Unit
            override fun onError(code: String) { error = code }
        })
        assertEquals("LIVE_PROVIDER_UNAVAILABLE", error)
        close.close()
    }
}
