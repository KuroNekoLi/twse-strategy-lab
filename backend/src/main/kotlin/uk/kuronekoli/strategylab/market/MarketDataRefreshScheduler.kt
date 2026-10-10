package uk.kuronekoli.strategylab.market

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class MarketDataRefreshScheduler(
    private val ingestion: MarketDataIngestionService,
    @Value("\${app.market-data.ingestion-enabled:false}") private val enabled: Boolean,
) {
    @Async
    @EventListener(ApplicationReadyEvent::class)
    fun seedOnStartup() {
        if (enabled) refreshSafely()
    }

    @Scheduled(cron = "\${app.market-data.refresh-cron:0 20 18 * * MON-FRI}", zone = "Asia/Taipei")
    fun refreshAfterClose() {
        if (enabled) refreshSafely()
    }

    private fun refreshSafely() {
        runCatching { ingestion.refreshLatest() }
            .onFailure { log.warn("Scheduled government open-data refresh failed; last-good rows remain available") }
    }

    companion object { private val log = LoggerFactory.getLogger(MarketDataRefreshScheduler::class.java) }
}
