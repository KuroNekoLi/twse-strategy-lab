package uk.kuronekoli.strategylab.market

import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.market-data.provider"], havingValue = "database", matchIfMissing = true)
class DatabaseHistoricalMarketDataProvider(
    private val store: MarketDataStore,
    @Value("\${app.market-data.allow-unverified-display:false}") private val allowUnverifiedDisplay: Boolean,
    private val backfill: MarketDataHistoryBackfillService? = null,
    @Value("\${app.market-data.on-demand-backfill-enabled:true}") private val onDemandBackfillEnabled: Boolean = true,
) : HistoricalMarketDataProvider {
    override val sourceName = GovernmentOpenDataDailySource.attributionFor(java.time.LocalDate.now().year)

    override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> =
        loadSnapshot(symbol, first, last).bars

    override fun loadSnapshot(symbol: String, first: YearMonth, last: YearMonth): HistoricalBarsSnapshot {
        ensureRangeCached(symbol, first, last)
        val result = store.findBars(symbol, first.atDay(1), last.atEndOfMonth())
            ?: return HistoricalBarsSnapshot(emptyList(), sourceName, GovernmentOpenDataDailySource.METADATA.datasetUrl, GovernmentOpenDataDailySource.METADATA.licenseUrl,
                GovernmentOpenDataDailySource.METADATA.licensingStatus, "未調整原始價格", java.time.Instant.now(), null, store.findEarliestDate(symbol),
                listOf("政府開放資料每日快照尚未累積此標的／期間的歷史資料。"))
        if (result.metadata.licensingStatus != "CONFIRMED" && !allowUnverifiedDisplay) {
            return HistoricalBarsSnapshot(
                bars = emptyList(), source = result.metadata.attribution, sourceUrl = result.metadata.datasetUrl,
                licenseUrl = result.metadata.licenseUrl, licensingStatus = result.metadata.licensingStatus,
                adjustmentPolicy = "未調整原始價格", fetchedAt = result.lastVerifiedAt,
                lastVerifiedAt = result.lastVerifiedAt, earliestAvailableDate = result.earliestAvailableDate,
                limitations = listOf("此資料來源的公開展示權尚未確認，因此目前不提供圖表資料。"),
            )
        }
        return HistoricalBarsSnapshot(
            bars = result.bars,
            source = result.metadata.attribution,
            sourceUrl = result.metadata.datasetUrl,
            licenseUrl = result.metadata.licenseUrl,
            licensingStatus = result.metadata.licensingStatus,
            adjustmentPolicy = "未調整原始價格",
            fetchedAt = result.lastVerifiedAt,
            lastVerifiedAt = result.lastVerifiedAt,
            earliestAvailableDate = result.earliestAvailableDate,
            limitations = listOf(
                "資料來源為 ${result.metadata.provider} 的 ${result.metadata.datasetTitle} 歷史行情；本站依資料授權狀態呈現。",
                "目前僅保存有成交的觀察列；缺少日期不代表休市或零成交，交易日覆蓋狀態未知。",
                "價格為未調整原始價格，未處理股利、分割及其他公司行動。",
            ),
        )
    }

    private fun ensureRangeCached(symbol: String, first: YearMonth, last: YearMonth) {
        if (!onDemandBackfillEnabled || backfill?.publicDisplayAllowed != true) return
        val from = first.atDay(1)
        val to = minOf(last.atEndOfMonth(), LocalDate.now(TAIPEI_ZONE))
        if (from.isAfter(to)) return

        symbolLocks[symbol.hashCode().and(Int.MAX_VALUE) % symbolLocks.size].withLock {
            val gaps = uncoveredRanges(from, to, store.findHistoricalCoverage(symbol, from, to))
            gaps.forEach { (gapFrom, gapTo) -> backfill.import(symbol, gapFrom, gapTo) }
        }
    }

    private fun uncoveredRanges(from: LocalDate, to: LocalDate, covered: List<HistoricalCoverage>): List<Pair<LocalDate, LocalDate>> {
        var cursor = from
        val gaps = mutableListOf<Pair<LocalDate, LocalDate>>()
        for (range in covered.sortedBy(HistoricalCoverage::from)) {
            if (range.to.isBefore(cursor) || range.from.isAfter(to)) continue
            if (range.from.isAfter(cursor)) gaps += cursor to minOf(to, range.from.minusDays(1))
            if (!range.to.isBefore(cursor)) cursor = range.to.plusDays(1)
            if (cursor.isAfter(to)) break
        }
        if (!cursor.isAfter(to)) gaps += cursor to to
        return gaps
    }

    companion object {
        private val symbolLocks = Array(64) { ReentrantLock() }
        private val TAIPEI_ZONE = ZoneId.of("Asia/Taipei")
    }
}
