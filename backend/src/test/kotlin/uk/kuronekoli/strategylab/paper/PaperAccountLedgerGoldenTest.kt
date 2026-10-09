package uk.kuronekoli.strategylab.paper

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PaperAccountLedgerGoldenTest {
    private val day=LocalDate.of(2026,1,5); private fun bd(v:String)=BigDecimal(v)
    private fun money(expected:String,actual:BigDecimal)=assertEquals(0,bd(expected).compareTo(actual))
    @Test fun cashAndPositionReconcileWithRoundedFeeAndTax() { val ledger=PaperAccountLedger(); ledger.append(PaperAccountEvent.initialDeposit("deposit-1",day,bd("1000.00"))); ledger.append(PaperAccountEvent.buy("buy-1",day,"2330",5,bd("100.00"),bd("0.01"))); ledger.append(PaperAccountEvent.sell("sell-1",day.plusDays(1),"2330",2,bd("120.00"),bd("0.01"),bd("0.003"))); val state=ledger.projection(); money("731.88",state.cash()); assertEquals(3,state.position("2330").quantity); money("303.00",state.position("2330").costBasis); assertEquals(3,ledger.events().size) }
    @Test fun rejectsInsufficientCashAndSharesWithoutChangingLedger() { val ledger=PaperAccountLedger(); ledger.append(PaperAccountEvent.initialDeposit("d",day,bd("99.99"))); assertThrows(IllegalArgumentException::class.java){ledger.append(PaperAccountEvent.buy("too",day,"2330",1,bd("100"),bd("0")))}; money("99.99",ledger.projection().cash()); ledger.append(PaperAccountEvent.buy("buy",day,"2330",1,bd("50"),bd("0"))); assertThrows(IllegalArgumentException::class.java){ledger.append(PaperAccountEvent.sell("oversell",day,"2330",2,bd("50"),bd("0"),bd("0")))}; assertEquals(2,ledger.events().size) }
    @Test fun rebuildIsDeterministicAndRejectsDuplicateIds() { val event=PaperAccountEvent.initialDeposit("d",day,bd("1000")); val events=listOf(event,PaperAccountEvent.buy("b",day,"2330",4,bd("100"),bd("0.01")),PaperAccountEvent.sell("s",day.plusDays(1),"2330",1,bd("110"),bd("0.01"),bd("0.003"))); val one=PaperAccountLedger.rebuild(events); val two=PaperAccountLedger.rebuild(events.toList()); assertEquals(one.cash(),two.cash()); money("704.57",one.cash()); assertEquals(one.positions(),two.positions()); assertThrows(IllegalArgumentException::class.java){PaperAccountLedger.rebuild(listOf(event,event))} }
    @Test fun rejectsValuesRoundedToZero() { assertThrows(IllegalArgumentException::class.java){PaperAccountEvent.initialDeposit("tiny",day,bd("0.004"))}; assertThrows(IllegalArgumentException::class.java){PaperAccountEvent.buy("tiny",day,"2330",1,bd("0.004"),bd("0"))} }
    @Test fun duplicateEventIdsAndRebuildFailureDoNotMutateLedger() { val ledger=PaperAccountLedger(); val deposit=PaperAccountEvent.initialDeposit("same",day,bd("200")); ledger.append(deposit); assertThrows(IllegalArgumentException::class.java){ledger.append(PaperAccountEvent.initialDeposit("same",day,bd("100")))}; assertEquals(listOf(deposit),ledger.events()); money("200.00",ledger.projection().cash()) }

}
