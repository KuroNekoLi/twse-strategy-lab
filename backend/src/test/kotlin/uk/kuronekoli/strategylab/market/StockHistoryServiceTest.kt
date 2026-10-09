package uk.kuronekoli.strategylab.market

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import uk.kuronekoli.strategylab.api.BacktestException

class StockHistoryServiceTest {
    @Test fun disabledHistoryGateReturns503WithoutCallingTheMarketSource() {
        val source = mock(TwseMarketDataClient::class.java)
        val service = StockHistoryService(source, Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneId.of("Asia/Taipei")), false)
        val error = assertThrows(BacktestException::class.java) { service.history("2330", "2025-01-01", "2025-10-01") }
        assertEquals("MARKET_HISTORY_DISABLED", error.code); assertEquals(503, error.status); verifyNoInteractions(source)
    }
}
