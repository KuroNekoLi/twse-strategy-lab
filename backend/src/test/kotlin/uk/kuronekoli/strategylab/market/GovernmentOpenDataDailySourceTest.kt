package uk.kuronekoli.strategylab.market

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import uk.kuronekoli.strategylab.api.BacktestException

class GovernmentOpenDataDailySourceTest {
    private val source = GovernmentOpenDataDailySource("https://example.invalid/market.csv")

    @Test
    fun `parses the published CSV contract and converts the Republic of China date`() {
        val rows = source.parseCsv(csv("\"1151008\",\"0050\",\"元大台灣50\",\"99877876\",\"11509275054\",\"115.35\",\"115.55\",\"114.90\",\"114.95\",\"-1.1000\",\"191636\""))

        assertEquals(1, rows.size)
        assertEquals("0050", rows.single().symbol)
        assertEquals(LocalDate.of(2026, 10, 8), rows.single().date)
        assertEquals(BigDecimal("115.35"), rows.single().open)
        assertEquals(BigDecimal("115.55"), rows.single().high)
        assertEquals(BigDecimal("114.90"), rows.single().low)
        assertEquals(BigDecimal("114.95"), rows.single().close)
        assertEquals(99_877_876L, rows.single().volume)
    }

    @Test
    fun `rejects changed schema duplicate instruments and invalid OHLC`() {
        val wrongHeader = assertThrows(BacktestException::class.java) { source.parseCsv("date,symbol,name\n") }
        assertEquals("UPSTREAM_DATA_UNAVAILABLE", wrongHeader.code)
        val row = "\"1151008\",\"0050\",\"元大台灣50\",\"10\",\"1000\",\"10\",\"9\",\"8\",\"9\",\"0\",\"1\""
        assertThrows(BacktestException::class.java) { source.parseCsv(csv("$row\n$row")) }
        assertThrows(BacktestException::class.java) {
            source.parseCsv(csv("\"1151008\",\"0050\",\"元大台灣50\",\"10\",\"1000\",\"10\",\"9\",\"8\",\"9\",\"0\",\"1\""))
        }
    }

    @Test
    fun `quoted fields with commas and escaped quotes remain aligned`() {
        val rows = source.parseCsv(csv("\"1151008\",\"0050\",\"元大,台灣\"\"50\",\"1,000\",\"115092\",\"115.35\",\"115.55\",\"114.90\",\"114.95\",\"0.00\",\"10\""))
        assertEquals("元大,台灣\"50", rows.single().name)
        assertEquals(1000L, rows.single().volume)
    }

    private fun csv(row: String) = """日期,證券代號,證券名稱,成交股數,成交金額,開盤價,最高價,最低價,收盤價,漲跌價差,成交筆數
$row
"""
}
