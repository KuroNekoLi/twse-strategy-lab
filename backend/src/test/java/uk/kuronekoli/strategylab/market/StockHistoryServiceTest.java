package uk.kuronekoli.strategylab.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import uk.kuronekoli.strategylab.api.BacktestException;

class StockHistoryServiceTest {
  @Test void disabledHistoryGateReturns503WithoutCallingTheMarketSource() {
    TwseMarketDataClient source = mock(TwseMarketDataClient.class);
    var service = new StockHistoryService(source, Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneId.of("Asia/Taipei")), false);
    BacktestException error = assertThrows(BacktestException.class, () -> service.history("2330", "2025-01-01", "2025-10-01"));
    assertEquals("MARKET_HISTORY_DISABLED", error.code());
    assertEquals(503, error.status());
    verifyNoInteractions(source);
  }
}
