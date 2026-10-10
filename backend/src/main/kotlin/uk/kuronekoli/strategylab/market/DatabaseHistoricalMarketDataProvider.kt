package uk.kuronekoli.strategylab.market

import java.time.YearMonth
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.market-data.provider"], havingValue = "database", matchIfMissing = true)
class DatabaseHistoricalMarketDataProvider(
    private val store: MarketDataStore,
    @Value("\${app.market-data.allow-unverified-display:false}") private val allowUnverifiedDisplay: Boolean,
) : HistoricalMarketDataProvider {
    override val sourceName = GovernmentOpenDataDailySource.attributionFor(java.time.LocalDate.now().year)

    override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> =
        loadSnapshot(symbol, first, last).bars

    override fun loadSnapshot(symbol: String, first: YearMonth, last: YearMonth): HistoricalBarsSnapshot {
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
}
