package uk.kuronekoli.strategylab.paper

import java.util.Collections

import java.time.Instant
import java.time.LocalDate

class OrderLifecycleAggregate(created: OrderLifecycleEvent) {
    enum class Status { CREATED, SUBMITTED, ACCEPTED, FILLED, REJECTED, CANCELLED, EXPIRED }
    private val orderId: String
    private val events = mutableListOf<OrderLifecycleEvent>()
    private val eventsById = mutableMapOf<String, OrderLifecycleEvent>()
    private var currentStatus: Status? = null
    private var lastOccurredAt: Instant? = null
    private var lastEffectiveDate: LocalDate? = null
    init {
        require(created.type == OrderLifecycleEvent.Type.CREATED) { "first event must be CREATED" }
        orderId = created.orderId
        append(created)
    }
    @Synchronized fun append(event: OrderLifecycleEvent) {
        val prior = eventsById[event.eventId]
        if (prior != null) { if (prior == event) return else throw IllegalArgumentException("eventId was already used with a different payload") }
        require(orderId == event.orderId) { "event orderId does not match aggregate" }
        val next = nextStatus(currentStatus, event.type) ?: throw IllegalArgumentException("illegal order transition: $currentStatus -> ${event.type}")
        require(lastOccurredAt == null || !event.occurredAt.isBefore(lastOccurredAt)) { "occurredAt must be monotonic" }
        require(lastEffectiveDate == null || !event.effectiveDate.isBefore(lastEffectiveDate)) { "effectiveDate must be monotonic" }
        events.add(event); eventsById[event.eventId] = event; currentStatus = next
        lastOccurredAt = event.occurredAt; lastEffectiveDate = event.effectiveDate
    }
    @Synchronized fun status(): Status = currentStatus!!
    fun orderId(): String = orderId
    @Synchronized fun events(): List<OrderLifecycleEvent> = Collections.unmodifiableList(events.toList())
    companion object {
        private fun nextStatus(current: Status?, type: OrderLifecycleEvent.Type): Status? = when (current) {
            null -> if (type == OrderLifecycleEvent.Type.CREATED) Status.CREATED else null
            Status.CREATED -> if (type == OrderLifecycleEvent.Type.SUBMITTED) Status.SUBMITTED else null
            Status.SUBMITTED -> when (type) { OrderLifecycleEvent.Type.ACCEPTED -> Status.ACCEPTED; OrderLifecycleEvent.Type.REJECTED -> Status.REJECTED; else -> null }
            Status.ACCEPTED -> when (type) { OrderLifecycleEvent.Type.FILLED -> Status.FILLED; OrderLifecycleEvent.Type.CANCELLED -> Status.CANCELLED; OrderLifecycleEvent.Type.EXPIRED -> Status.EXPIRED; else -> null }
            else -> null
        }
    }
}
