package uk.kuronekoli.strategylab.market

import java.time.YearMonth
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["app.market-data.provider"], havingValue = "database", matchIfMissing = true)
class DatabaseHistoricalMarketDataProvider(private val store: MarketDataStore) : HistoricalMarketDataProvider {
    override val sourceName = GovernmentOpenDataDailySource.attributionFor(java.time.LocalDate.now().year)

    override fun load(symbol: String, first: YearMonth, last: YearMonth): List<DailyBar> =
        loadSnapshot(symbol, first, last).bars

    override fun loadSnapshot(symbol: String, first: YearMonth, last: YearMonth): HistoricalBarsSnapshot {
        val result = store.findBars(symbol, first.atDay(1), last.atEndOfMonth())
            ?: return HistoricalBarsSnapshot(emptyList(), sourceName, GovernmentOpenDataDailySource.METADATA.datasetUrl, GovernmentOpenDataDailySource.METADATA.licenseUrl,
                "CONFIRMED", "未調整原始價格", java.time.Instant.now(), null, store.findEarliestDate(symbol),
                listOf("政府開放資料每日快照尚未累積此標的／期間的歷史資料。"))
        return HistoricalBarsSnapshot(
            bars = result.bars,
            source = result.metadata.attribution,
            sourceUrl = result.metadata.datasetUrl,
            licenseUrl = result.metadata.licenseUrl,
            licensingStatus = "CONFIRMED",
            adjustmentPolicy = "未調整原始價格",
            fetchedAt = result.lastVerifiedAt,
            lastVerifiedAt = result.lastVerifiedAt,
            earliestAvailableDate = result.earliestAvailableDate,
            limitations = listOf(
                "資料來源為政府資料開放平臺之每日上市行情快照；本站自開始匯入日起累積，沒有 2010 年起的歷史回補。",
                "目前僅保存有成交的觀察列；缺少日期不代表休市或零成交，交易日覆蓋狀態未知。",
                "價格為未調整原始價格，未處理股利、分割及其他公司行動。",
            ),
        )
    }
}
