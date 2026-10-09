package uk.kuronekoli.strategylab.paper

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections

class PaperAccountProjection {
    data class Position(val quantity: Long, val costBasis: BigDecimal)
    private var cash = ZERO
    private val mutablePositions = linkedMapOf<String, Position>()
    private var initialized = false
    fun cash(): BigDecimal = cash
    fun positions(): Map<String, Position> = Collections.unmodifiableMap(mutablePositions)
    fun position(symbol: String): Position = mutablePositions[symbol] ?: Position(0, ZERO)
    internal fun apply(event: PaperAccountEvent) { when (event.type) { PaperAccountEvent.Type.INITIAL_DEPOSIT -> deposit(event); PaperAccountEvent.Type.BUY_FILL -> buy(event); PaperAccountEvent.Type.SELL_FILL -> sell(event) } }
    private fun deposit(event: PaperAccountEvent) { require(!initialized) { "initial deposit already recorded" }; initialized = true; cash = cash.add(money(event.amount!!)) }
    private fun buy(event: PaperAccountEvent) {
        requireInitialized(); val current = position(event.symbol!!)
        require(Long.MAX_VALUE - current.quantity >= event.quantity) { "position quantity overflow" }
        val gross = money(event.price!!.multiply(BigDecimal.valueOf(event.quantity))); val fee = money(gross.multiply(event.commissionRate!!)); val total = gross.add(fee)
        require(cash >= total) { "insufficient cash" }; cash = cash.subtract(total)
        mutablePositions[event.symbol] = Position(current.quantity + event.quantity, current.costBasis.add(total))
    }
    private fun sell(event: PaperAccountEvent) {
        requireInitialized(); val current = position(event.symbol!!); require(current.quantity >= event.quantity) { "insufficient shares" }
        val gross = money(event.price!!.multiply(BigDecimal.valueOf(event.quantity))); val fee = money(gross.multiply(event.commissionRate!!)); val tax = money(gross.multiply(event.sellTaxRate!!))
        cash = cash.add(gross.subtract(fee).subtract(tax)); val remaining = current.quantity - event.quantity
        val remainingCost = if (remaining == 0L) ZERO else current.costBasis.multiply(BigDecimal.valueOf(remaining)).divide(BigDecimal.valueOf(current.quantity), 2, RoundingMode.HALF_UP)
        if (remaining == 0L) mutablePositions.remove(event.symbol) else mutablePositions[event.symbol] = Position(remaining, remainingCost)
    }
    private fun requireInitialized() { require(initialized) { "initial deposit must be first" } }
    companion object { private val ZERO = BigDecimal("0.00"); internal fun money(value: BigDecimal) = value.setScale(2, RoundingMode.HALF_UP) }
}
