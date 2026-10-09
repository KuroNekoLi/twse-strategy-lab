package uk.kuronekoli.strategylab.robustness

import java.math.BigDecimal
import uk.kuronekoli.strategylab.api.BacktestException
import uk.kuronekoli.strategylab.api.BacktestRequest
import uk.kuronekoli.strategylab.backtest.BacktestEngine
import uk.kuronekoli.strategylab.backtest.BacktestValidation
import uk.kuronekoli.strategylab.market.DailyBar

/** Bounded, deterministic, offline sensitivity analysis over caller-supplied bars. */
object RobustnessMatrix {
    const val MAX_CASES = 25
    @JvmRecord data class Variant(val id: String?, val fastWindow: Int?, val slowWindow: Int?, val commissionRate: BigDecimal?, val sellTaxRate: BigDecimal?)
    @JvmRecord data class Metrics(val totalReturnPercent: Double, val annualizedReturnPercent: Double, val maxDrawdownPercent: Double, val annualizedRealizedVolatilityPercent: Double)
    @JvmRecord data class CaseResult(val id: String, val sampleCount: Int, val metrics: Metrics)
    @JvmRecord data class Range(val min: Double, val median: Double, val max: Double)
    @JvmRecord data class Aggregate(val totalReturnPercent: Range, val annualizedReturnPercent: Range, val maxDrawdownPercent: Range, val annualizedRealizedVolatilityPercent: Range)
    @JvmRecord data class Result(val cases: List<CaseResult>, val aggregate: Aggregate)

    @JvmStatic fun run(base: BacktestRequest?, symbol: String?, suppliedBars: List<DailyBar>?, appliedTaxRate: BigDecimal?, variants: List<Variant>?): Result {
        if(base==null || symbol.isNullOrBlank()) throw BacktestException.input("請提供有效的回測基準與標的。")
        BacktestValidation.bars(suppliedBars)
        validateVariants(base,appliedTaxRate,variants)
        val results=variants!!.map { variant ->
            val request=apply(base,variant); val tax=variant.sellTaxRate ?: appliedTaxRate!!
            val active=BacktestEngine.run(request,symbol,suppliedBars!!,tax)[0]
            val annualized=active.annualizedReturn; val volatility=active.annualizedRealizedVolatility
            if(annualized==null || volatility==null || !annualized.isFinite() || !volatility.isFinite() || !active.totalReturn.isFinite() || !active.maxDrawdown.isFinite()) throw BacktestException.input("此案例的回測指標超出可表示範圍，無法納入穩健性摘要。")
            CaseResult(variant.id!!,active.dailyEquity.size,Metrics(active.totalReturn,annualized,active.maxDrawdown,volatility))
        }
        return Result(results,aggregate(results))
    }
    @JvmStatic fun validateVariants(base: BacktestRequest?, appliedTaxRate: BigDecimal?, variants: List<Variant>?) {
        if(base==null) throw BacktestException.input("請提供有效的回測基準。")
        if(variants.isNullOrEmpty() || variants.size>MAX_CASES) throw BacktestException.input("穩健性矩陣需包含 1 至 $MAX_CASES 個案例。")
        val ids=mutableSetOf<String>(); val configurations=mutableSetOf<String>()
        variants.forEach { v ->
            validateVariant(v,base); if(!ids.add(v.id!!)) throw BacktestException.input("穩健性矩陣案例識別碼不可重複。")
            val request=apply(base,v); BacktestValidation.parameters(request); val tax=v.sellTaxRate ?: appliedTaxRate!!
            BacktestValidation.costs(request.commissionRate,tax)
            val configuration="${request.fastWindow}|${request.slowWindow}|${request.commissionRate!!.stripTrailingZeros().toPlainString()}|${tax.stripTrailingZeros().toPlainString()}"
            if(!configurations.add(configuration)) throw BacktestException.input("穩健性矩陣不可包含重複參數案例。")
        }
    }
    private fun validateVariant(v: Variant?,base: BacktestRequest) {
        if(v?.id==null || !v.id.matches(Regex("[A-Za-z0-9_-]{1,40}"))) throw BacktestException.input("案例識別碼需為 1 至 40 個英數字、底線或連字號。")
        if(v.fastWindow==null && v.slowWindow==null && v.commissionRate==null && v.sellTaxRate==null) throw BacktestException.input("每個案例至少需覆寫一個策略參數或成本。")
        if((v.fastWindow!=null || v.slowWindow!=null) && base.strategyOrDefault()!="ma-crossover") throw BacktestException.input("只有雙均線交叉策略可覆寫快慢均線日數。")
    }
    private fun apply(b: BacktestRequest,v: Variant)=BacktestRequest(b.symbol,b.symbols,b.from,b.to,b.strategy,v.fastWindow?:b.fastWindow,v.slowWindow?:b.slowWindow,b.rsiWindow,b.rsiBuyThreshold,b.rsiSellThreshold,b.bollingerWindow,b.bollingerMultiplier,b.breakoutWindow,b.drawdownBuyPercent,b.profitSellPercent,b.monthlyContribution,b.initialCapital,v.commissionRate?:b.commissionRate,v.sellTaxRate?:b.sellTaxRate,b.useMarketTaxDefaults)
    private fun aggregate(cases: List<CaseResult>)=Aggregate(range(cases.map{it.metrics.totalReturnPercent}),range(cases.map{it.metrics.annualizedReturnPercent}),range(cases.map{it.metrics.maxDrawdownPercent}),range(cases.map{it.metrics.annualizedRealizedVolatilityPercent}))
    private fun range(values: List<Double>): Range { val sorted=values.sorted(); val n=sorted.size; val median=if(n%2==1) sorted[n/2] else sorted[n/2-1]/2+sorted[n/2]/2; return Range(sorted.first(),median,sorted.last()) }
}
