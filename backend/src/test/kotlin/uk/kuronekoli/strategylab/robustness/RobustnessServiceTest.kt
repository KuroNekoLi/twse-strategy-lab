package uk.kuronekoli.strategylab.robustness

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.market.DailyBar
import uk.kuronekoli.strategylab.market.HistoricalMarketDataProvider

class RobustnessServiceTest {
    private val clock=Clock.fixed(Instant.parse("2024-02-15T04:00:00Z"),ZoneId.of("Asia/Taipei")); private fun bd(s:String)=BigDecimal(s)
    private fun base(to:String)=BacktestRequest("2330",null,"2024-01-01",to,"ma-crossover",2,4,null,null,null,null,null,null,null,null,BigDecimal.ZERO,BigDecimal("10000"),BigDecimal.ZERO,BigDecimal.ZERO,false)
    private fun bars()=(0 until 25).map{DailyBar(LocalDate.of(2024,1,1).plusDays(it.toLong()),BigDecimal.valueOf((100+it).toLong()))}
    private fun request(to:String)=RobustnessRequest("base-v1",base(to),listOf(RobustnessMatrix.Variant("base-cost",null,null,BigDecimal.ZERO,null),RobustnessMatrix.Variant("higher-cost",null,null,bd("0.001"),bd("0.003"))))
    @Test fun loadsOnceAndReturnsSamplesAndCaveats() { val client=provider(bars()); val response=RobustnessService(client,clock).run(request("2024-12-31")); assertEquals("base-v1",response.baseId); assertEquals("LIMITED_RESEARCH",response.status); assertEquals(25,response.sampleCount); assertEquals("2024-01-25",response.observedTo); assertEquals("synthetic test source",response.dataSource); assertEquals(listOf("base-cost","higher-cost"),response.cases.map{it.id}); assertTrue(response.limitations.any{it.contains("LICENSING_UNVERIFIED")}); assertTrue(response.limitations.any{it.contains("不代表策略有效")}) }
    @Test fun rejectsInvalidRequestsBeforeFetchingData() { val client=Mockito.mock(HistoricalMarketDataProvider::class.java); val service=RobustnessService(client,clock); val bad=RobustnessRequest("base-v1",base("not-a-date"),request("2024-01-10").variants); assertEquals("INVALID_INPUT",assertThrows(BacktestException::class.java){service.run(bad)}.code); val oversized=(0..RobustnessMatrix.MAX_CASES).map{RobustnessMatrix.Variant("case-$it",2+it,40,null,null)}; assertEquals("INVALID_INPUT",assertThrows(BacktestException::class.java){service.run(RobustnessRequest("base-v1",base("2024-01-10"),oversized))}.code); Mockito.verifyNoInteractions(client) }
    @Test fun reportsEmptyProviderDataAsNoMarketData() { val client=provider(emptyList()); val error=assertThrows(uk.kuronekoli.strategylab.api.ApiExceptionHandler.NoMarketDataException::class.java){RobustnessService(client,clock).run(request("2024-01-10"))}; assertEquals("2330 在這段期間沒有可用日行情；上市、停牌與日曆狀態未知。",error.message) }

    private fun provider(data: List<DailyBar>) = object : HistoricalMarketDataProvider {
        override val sourceName = "synthetic test source"
        override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> = data
    }
}
