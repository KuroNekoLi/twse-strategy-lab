package uk.kuronekoli.strategylab.replay

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections
import uk.kuronekoli.strategylab.market.DailyBar

class WalkForwardReplay(bars: List<DailyBar>, startingCash: BigDecimal, feeRate: BigDecimal) {
    enum class Action { WAIT, BUY, SELL }
    @JvmRecord data class Observation(val date: java.time.LocalDate, val close: BigDecimal)
    @JvmRecord data class Decision(val action: Action, val quantity: BigDecimal) {
        init { require(quantity.signum() >= 0) { "數量不可為負數。" }; require(action != Action.WAIT || quantity.signum() == 0) { "觀望數量必須為零。" }; require(action == Action.WAIT || quantity.signum() > 0) { "買賣數量必須大於零。" } }
    }
    @JvmRecord data class Event(val date: java.time.LocalDate, val observedClose: BigDecimal, val action: Action, val quantity: BigDecimal, val gross: BigDecimal, val fee: BigDecimal, val cashAfter: BigDecimal, val sharesAfter: BigDecimal)
    @JvmRecord data class State(val observation: Observation?, val cash: BigDecimal, val shares: BigDecimal, val events: List<Event>, val complete: Boolean)
    private val bars: List<DailyBar>
    private val feeRate = feeRate
    private var cursor = 0
    private var cash = startingCash
    private var shares = BigDecimal.ZERO
    private val events = mutableListOf<Event>()
    init {
        require(bars.isNotEmpty()) { "至少需要一筆日收盤資料。" }
        this.bars = bars.toList()
        this.bars.forEachIndexed { i, bar -> require(bar.date != null && bar.close != null && bar.close.signum() > 0) { "日期及收盤價必須有效，收盤價須大於零。" }; require(i == 0 || this.bars[i-1].date.isBefore(bar.date)) { "日資料必須依日期嚴格遞增且不可重複。" } }
        require(startingCash.signum() >= 0) { "起始現金不可為負數。" }
        require(feeRate.signum() >= 0 && feeRate < BigDecimal.ONE) { "費率必須介於 0（含）與 1（不含）之間。" }
    }
    @Synchronized fun observe(): Observation? = if (cursor < bars.size) Observation(bars[cursor].date, bars[cursor].close) else null
    @Synchronized fun decide(decision: Decision): State {
        check(cursor < bars.size) { "練習已完成。" }
        val bar = bars[cursor]; val gross = bar.close.multiply(decision.quantity); val fee = gross.multiply(feeRate).setScale(2, RoundingMode.HALF_UP)
        when (decision.action) {
            Action.WAIT -> Unit
            Action.BUY -> { val debit = gross.add(fee); require(debit <= cash) { "現金不足，買入未執行且游標未前進。" }; cash = cash.subtract(debit); shares = shares.add(decision.quantity) }
            Action.SELL -> { require(decision.quantity <= shares) { "持有股數不足，賣出未執行且游標未前進。" }; cash = cash.add(gross.subtract(fee)); shares = shares.subtract(decision.quantity) }
        }
        events.add(Event(bar.date, bar.close, decision.action, decision.quantity, gross, fee, cash, shares)); cursor++
        return state()
    }
    @Synchronized fun state(): State = State(observe(), cash, shares, Collections.unmodifiableList(events.toList()), cursor >= bars.size)
    companion object {
        @JvmStatic fun reconstruct(privateBars: List<DailyBar>, startingCash: BigDecimal, feeRate: BigDecimal, decisions: List<Decision>): State { val replay=WalkForwardReplay(privateBars, startingCash, feeRate); decisions.forEach(replay::decide); return replay.state() }
    }
}
