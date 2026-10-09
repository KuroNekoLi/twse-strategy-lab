package uk.kuronekoli.strategylab.paper

import java.time.Instant
import java.time.LocalDate

data class OrderLifecycleEvent(val eventId: String, val orderId: String, val type: Type, val occurredAt: Instant, val effectiveDate: LocalDate, val reason: String?) {
    enum class Type { CREATED, SUBMITTED, ACCEPTED, FILLED, REJECTED, CANCELLED, EXPIRED }
    init {
        require(eventId.isNotBlank()) { "eventId is required" }
        require(orderId.isNotBlank()) { "orderId is required" }
        requireNotNull(type) { "type is required" }
        requireNotNull(occurredAt) { "occurredAt is required" }
        requireNotNull(effectiveDate) { "effectiveDate is required" }
    }
}
