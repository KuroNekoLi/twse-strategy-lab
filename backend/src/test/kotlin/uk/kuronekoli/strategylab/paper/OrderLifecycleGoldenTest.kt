package uk.kuronekoli.strategylab.paper

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OrderLifecycleGoldenTest {
    private val t0=Instant.parse("2026-01-05T01:00:00Z"); private val d0=LocalDate.of(2026,1,5)
    private fun event(id:String,type:OrderLifecycleEvent.Type,minute:Int)=OrderLifecycleEvent(id,"order-1",type,t0.plusSeconds(minute*60L),d0,null)
    private fun created()=OrderLifecycleAggregate(event("created",OrderLifecycleEvent.Type.CREATED,0))
    private fun accepted()=created().also { it.append(event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1)); it.append(event("accepted",OrderLifecycleEvent.Type.ACCEPTED,2)) }
    @Test fun supportsCreatedSubmittedAcceptedFilledPath() { val order=created(); assertEquals(OrderLifecycleAggregate.Status.CREATED,order.status()); order.append(event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1)); assertEquals(OrderLifecycleAggregate.Status.SUBMITTED,order.status()); order.append(event("accepted",OrderLifecycleEvent.Type.ACCEPTED,2)); assertEquals(OrderLifecycleAggregate.Status.ACCEPTED,order.status()); order.append(event("filled",OrderLifecycleEvent.Type.FILLED,3)); assertEquals(OrderLifecycleAggregate.Status.FILLED,order.status()); assertEquals(listOf("created","submitted","accepted","filled"),order.events().map{it.eventId}); assertThrows(UnsupportedOperationException::class.java){(order.events() as MutableList).add(event("extra",OrderLifecycleEvent.Type.FILLED,4))} }
    @Test fun supportsRejectedAndTerminalExits() { val rejected=created(); rejected.append(event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1)); rejected.append(event("rejected",OrderLifecycleEvent.Type.REJECTED,2)); assertEquals(OrderLifecycleAggregate.Status.REJECTED,rejected.status()); listOf(OrderLifecycleEvent.Type.CANCELLED,OrderLifecycleEvent.Type.EXPIRED).forEach { val order=accepted(); order.append(event(it.name,it,3)); assertEquals(OrderLifecycleAggregate.Status.valueOf(it.name),order.status()) } }
    @Test fun rejectsIllegalTransitionsWithoutMutation() { val order=created(); listOf(OrderLifecycleEvent.Type.ACCEPTED,OrderLifecycleEvent.Type.FILLED,OrderLifecycleEvent.Type.REJECTED,OrderLifecycleEvent.Type.CANCELLED,OrderLifecycleEvent.Type.EXPIRED,OrderLifecycleEvent.Type.CREATED).forEach { assertThrows(IllegalArgumentException::class.java){order.append(event("bad-$it",it,1))}; assertEquals(OrderLifecycleAggregate.Status.CREATED,order.status()); assertEquals(1,order.events().size) }; val submitted=created(); submitted.append(event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1)); listOf(OrderLifecycleEvent.Type.CREATED,OrderLifecycleEvent.Type.SUBMITTED,OrderLifecycleEvent.Type.FILLED,OrderLifecycleEvent.Type.CANCELLED,OrderLifecycleEvent.Type.EXPIRED).forEach{assertThrows(IllegalArgumentException::class.java){submitted.append(event("bad-$it",it,2))}} }
    @Test fun duplicateEventsAreIdempotentButConflictsRejected() { val order=created(); val submitted=event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1); order.append(submitted); order.append(submitted); assertEquals(2,order.events().size); val conflict=OrderLifecycleEvent("submitted","order-1",OrderLifecycleEvent.Type.SUBMITTED,t0.plusSeconds(90),d0,"different"); assertThrows(IllegalArgumentException::class.java){order.append(conflict)} }
    @Test fun timesDatesAndInitialEventAreValidated() { assertThrows(IllegalArgumentException::class.java){OrderLifecycleAggregate(event("bad",OrderLifecycleEvent.Type.SUBMITTED,0))}; val order=created(); assertThrows(IllegalArgumentException::class.java){order.append(OrderLifecycleEvent("old","order-1",OrderLifecycleEvent.Type.SUBMITTED,t0.minusSeconds(1),d0,null))}; order.append(event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1)); assertThrows(IllegalArgumentException::class.java){order.append(OrderLifecycleEvent("bad-date","order-1",OrderLifecycleEvent.Type.ACCEPTED,t0.plusSeconds(120),d0.minusDays(1),null))}; assertEquals(2,order.events().size) }
    @Test fun terminalStatusesRejectAllFurtherEvents() {
        listOf(OrderLifecycleEvent.Type.FILLED,OrderLifecycleEvent.Type.REJECTED,OrderLifecycleEvent.Type.CANCELLED,OrderLifecycleEvent.Type.EXPIRED).forEach { terminal ->
            val order=created(); order.append(event("submitted",OrderLifecycleEvent.Type.SUBMITTED,1)); if(terminal==OrderLifecycleEvent.Type.REJECTED) order.append(event("terminal",terminal,2)) else { order.append(event("accepted",OrderLifecycleEvent.Type.ACCEPTED,2)); order.append(event("terminal",terminal,3)) }
            val before=order.events().size; OrderLifecycleEvent.Type.values().forEach { attempted -> assertThrows(IllegalArgumentException::class.java){order.append(event("after-$terminal-$attempted",attempted,10))}; assertEquals(before,order.events().size) }
        }
    }
    @Test fun rejectsWrongOrderAndMissingOccurrenceFields() {
        assertThrows(IllegalArgumentException::class.java){created().append(OrderLifecycleEvent("foreign","other",OrderLifecycleEvent.Type.SUBMITTED,t0.plusSeconds(60),d0,null))}
    }

}
