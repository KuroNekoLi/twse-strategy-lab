package uk.kuronekoli.strategylab.market;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.kuronekoli.strategylab.api.BacktestException;

/** Offline synthetic JSON only; these tests do not establish live provider coverage. */
class TwseMarketDataClientTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final TwseMarketDataClient client = new TwseMarketDataClient(mapper, "https://example.invalid/STOCK_DAY");
  private final YearMonth month = YearMonth.of(2024, 1);
  private String row(String date, String price) { return "[\"" + date + "\",\"0\",\"0\",\"0\",\"0\",\"0\",\"" + price + "\"]"; }
  @Test void rocDatesAndCommaDecimalPricesAreParsedExactly() {
    var bars = client.parseMonthResponse("2330", month, mapper.readTree("{\"stat\":\"OK\",\"data\":[" + row("113/01/02", "1,000.50") + "," + row("113/01/03", "1001.00") + "]}"));
    assertEquals("2024-01-02", bars.get(0).date().toString()); assertEquals(0, new BigDecimal("1000.50").compareTo(bars.get(0).close())); assertEquals(2, bars.size());
  }
  @Test void emptyMissingAndNonOkMonthlyResponsesNeverBecomeSilentEmptySuccess() {
    for (String response : new String[]{"{}", "null", "[]", "{\"stat\":\"查無資料\"}", "{\"stat\":\"OK\"}", "{\"stat\":\"OK\",\"data\":[]}", "{\"stat\":\"OK\",\"data\":{}}"}) {
      var error = assertThrows(BacktestException.class, () -> client.parseMonthResponse("2330", month, mapper.readTree(response)));
      assertTrue(error.code().startsWith("UPSTREAM_")); assertEquals(502, error.status()); assertTrue(error.getMessage().contains("2330／2024-01"));
    }
  }
  @Test void malformedRowsIncludingUnknownSuspensionPriceStopTheWholeMonth() {
    for (String badRow : new String[]{"[]", row("bad", "100"), row("113/02/01", "100"), row("113/01/32", "100"), row("113/01/03", "--"), row("113/01/03", "0"), row("113/01/03", "NaN"), row("113/01/03", "10,00")}) {
      String body = "{\"stat\":\"OK\",\"data\":[" + row("113/01/02", "100") + "," + badRow + "]}";
      assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException.class, () -> client.parseMonthResponse("2330", month, mapper.readTree(body))).code());
    }
  }
  @Test void duplicateAndUnorderedDatesAreRejectedRatherThanSortedOrDeduplicated() {
    for (String rows : new String[]{row("113/01/02", "100") + "," + row("113/01/02", "100"), row("113/01/03", "100") + "," + row("113/01/02", "100")}) {
      assertEquals("DATA_INTEGRITY_FAILED", assertThrows(BacktestException.class, () -> client.parseMonthResponse("2330", month, mapper.readTree("{\"stat\":\"OK\",\"data\":[" + rows + "]}"))).code());
    }
  }
}
