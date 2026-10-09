package uk.kuronekoli.strategylab.paper

import java.util.Collections

class PaperAccountLedger {
    private val events = mutableListOf<PaperAccountEvent>()
    private val eventIds = mutableSetOf<String>()
    @Synchronized fun append(event: PaperAccountEvent) { require(eventIds.add(event.eventId)) { "duplicate eventId" }; try { rebuildWith(events, event) } catch (e: Exception) { eventIds.remove(event.eventId); throw e }; events.add(event) }
    @Synchronized fun events(): List<PaperAccountEvent> = Collections.unmodifiableList(events.toList())
    @Synchronized fun projection(): PaperAccountProjection = rebuildWith(events, null)
    companion object {
        @JvmStatic fun rebuild(events: List<PaperAccountEvent>) = rebuildWith(events, null)
        private fun rebuildWith(events: List<PaperAccountEvent>, appended: PaperAccountEvent?): PaperAccountProjection {
            val result = PaperAccountProjection(); val seen = mutableSetOf<String>()
            for (event in events) { require(seen.add(event.eventId)) { "duplicate eventId" }; result.apply(event) }
            if (appended != null) { require(seen.add(appended.eventId)) { "duplicate eventId" }; result.apply(appended) }
            return result
        }
    }
}
