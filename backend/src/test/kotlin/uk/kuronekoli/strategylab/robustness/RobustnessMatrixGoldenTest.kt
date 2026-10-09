package uk.kuronekoli.strategylab.robustness

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.market.DailyBar

class RobustnessMatrixGoldenTest {
    private fun bd(v:String)=BigDecimal(v)
    private fun base(strategy:String)=BacktestRequest("2330",null,"2024-01-01","2024-12-31",strategy,2,4,2,30.0,55.0,3,2.0,2,20.0,20.0,bd("0"),bd("10000"),bd("0"),bd("0"),false)
    private fun bars()=(0 until 80).map { i -> DailyBar(LocalDate.of(2024,1,1).plusDays(i.toLong()),BigDecimal.valueOf(100+i*.08+ kotlin.math.sin(i*.71)*9).setScale(4,java.math.RoundingMode.HALF_UP)) }
    @Test fun costVariantsAreFiniteAndSensitive() { val out=RobustnessMatrix.run(base("ma-crossover"),"2330",bars(),bd("0"),listOf(RobustnessMatrix.Variant("no-cost",null,null,bd("0"),bd("0")),RobustnessMatrix.Variant("with-cost",null,null,bd("0.02"),bd("0.01")))); assertEquals(listOf("no-cost","with-cost"),out.cases.map{it.id}); assertEquals(80,out.cases[0].sampleCount); assertNotEquals(out.cases[0].metrics.totalReturnPercent,out.cases[1].metrics.totalReturnPercent); assertTrue(out.cases.all{listOf(it.metrics.totalReturnPercent,it.metrics.annualizedReturnPercent,it.metrics.maxDrawdownPercent,it.metrics.annualizedRealizedVolatilityPercent).all(Double::isFinite)}) }
    @Test fun sameInputsProduceSameOutputAndRejectInvalidMatrices() { val variants=listOf(RobustnessMatrix.Variant("slower",3,6,null,null),RobustnessMatrix.Variant("costlier",null,null,bd("0.001"),bd("0.003"))); assertEquals(RobustnessMatrix.run(base("ma-crossover"),"2330",bars(),bd("0"),variants),RobustnessMatrix.run(base("ma-crossover"),"2330",bars(),bd("0"),variants)); assertThrows(RuntimeException::class.java){RobustnessMatrix.run(base("ma-crossover"),"2330",bars(),bd("0"),emptyList())}; assertThrows(RuntimeException::class.java){RobustnessMatrix.run(base("rsi-reversion"),"2330",bars(),bd("0"),listOf(RobustnessMatrix.Variant("wrong",3,6,null,null)))}; assertThrows(RuntimeException::class.java){RobustnessMatrix.run(base("ma-crossover"),"2330",bars(),bd("0"),listOf(RobustnessMatrix.Variant("bad",9,4,null,null)))}; assertThrows(RuntimeException::class.java){RobustnessMatrix.run(base("ma-crossover"),"2330",bars(),bd("0"),listOf(RobustnessMatrix.Variant("duplicate",3,6,null,null),RobustnessMatrix.Variant("duplicate",4,6,null,null)))} }
}
