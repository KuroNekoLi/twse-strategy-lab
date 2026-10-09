package uk.kuronekoli.strategylab.paper

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class PaperAccountEvent(val eventId: String, val effectiveDate: LocalDate, val type: Type, val symbol: String?, val amount: BigDecimal?, val quantity: Long, val price: BigDecimal?, val commissionRate: BigDecimal?, val sellTaxRate: BigDecimal?) {
    enum class Type { INITIAL_DEPOSIT, BUY_FILL, SELL_FILL }
    init {
        require(eventId.isNotBlank()) { "eventId is required" }
        requireNotNull(effectiveDate) { "effectiveDate is required" }
        requireNotNull(type) { "type is required" }
        if (type == Type.INITIAL_DEPOSIT) {
            require(amount != null && amount.setScale(2, RoundingMode.HALF_UP).signum() > 0) { "deposit must be at least one cent" }
            require(symbol == null && quantity == 0L && price == null && commissionRate == null && sellTaxRate == null) { "deposit cannot contain fill fields" }
        } else {
            require(!symbol.isNullOrBlank()) { "symbol is required" }
            require(quantity > 0) { "quantity must be positive" }
            require(price != null && price.setScale(2, RoundingMode.HALF_UP).signum() > 0) { "price must be at least one cent" }
            rate(commissionRate, "commissionRate")
            rate(sellTaxRate, "sellTaxRate")
            require(type != Type.SELL_FILL || commissionRate!!.add(sellTaxRate!!).compareTo(BigDecimal.ONE) <= 0) { "combined sell charges cannot exceed gross value" }
            require(amount == null) { "fill cannot contain deposit amount" }
            require(type != Type.BUY_FILL || sellTaxRate!!.signum() == 0) { "buy fill cannot contain sell tax" }
        }
    }
    companion object {
        @JvmStatic fun initialDeposit(id: String, date: LocalDate, amount: BigDecimal) = PaperAccountEvent(id, date, Type.INITIAL_DEPOSIT, null, amount, 0, null, null, null)
        @JvmStatic fun buy(id: String, date: LocalDate, symbol: String, quantity: Long, price: BigDecimal, commissionRate: BigDecimal) = PaperAccountEvent(id, date, Type.BUY_FILL, symbol, null, quantity, price, commissionRate, BigDecimal.ZERO)
        @JvmStatic fun sell(id: String, date: LocalDate, symbol: String, quantity: Long, price: BigDecimal, commissionRate: BigDecimal, sellTaxRate: BigDecimal) = PaperAccountEvent(id, date, Type.SELL_FILL, symbol, null, quantity, price, commissionRate, sellTaxRate)
        private fun rate(value: BigDecimal?, field: String) { require(value != null && value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0) { "$field must be between 0 and 1" } }
    }
}
