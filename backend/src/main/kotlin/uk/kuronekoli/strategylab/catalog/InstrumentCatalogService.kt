package uk.kuronekoli.strategylab.catalog

import java.text.Normalizer
import java.util.Comparator
import java.util.Locale
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument

@Service
class InstrumentCatalogService @Autowired constructor(private val catalog: InstrumentCatalogClient, private val store: InstrumentCatalogStore) {
    constructor(catalog: InstrumentCatalogClient) : this(catalog, MemoryInstrumentCatalogStore())
    private val log = LoggerFactory.getLogger(InstrumentCatalogService::class.java)

    /** Fetches and validates every source before atomically replacing the persisted snapshot. */
    @Synchronized fun refresh() {
        val snapshots = catalog.snapshots()
        store.replace(snapshots)
        log.info("Refreshed instrument catalog: {} items from {} sources", snapshots.sumOf { it.rows.size }, snapshots.size)
    }
    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Taipei")
    fun refreshDaily() { try { refresh() } catch (_: RuntimeException) { log.warn("Daily instrument catalog refresh failed; the last persisted catalog remains available") } }

    fun search(query: String?, limit: Int): InstrumentCatalogResponse {
        if (query == null || query.codePointCount(0, query.length) > 60 || query.codePoints().anyMatch(Character::isISOControl)) throw CatalogException.input("請輸入最多 60 字的代碼或名稱。")
        val normalized = Normalizer.normalize(query.trim(), Normalizer.Form.NFKC)
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length) > 60 || limit < 1 || limit > 50) throw CatalogException.input("請提供最多 60 字的代碼或名稱；每次查詢筆數需為 1 至 50。")
        val needle = normalized.lowercase(Locale.ROOT)
        if (store.isEmpty()) refresh()
        var matches = store.matching(needle)
        val seen = HashSet<String>()
        for (item in matches) if (!seen.add(item.code)) throw CatalogException("CATALOG_CODE_CONFLICT", "不同市場名錄含相同代碼，已停止查詢以避免錯誤識別。", 502)
        matches = matches.sortedWith(compareBy<Instrument> { rank(it, needle) }.thenBy { it.code }.thenBy { it.market })
        return InstrumentCatalogResponse(normalized, limit, matches.size, matches.take(limit), store.sources(), listOf(
            "名錄含上市公司、上市基金與上櫃公司；來源更新頻率不同，fetchedAt 代表本服務取得時間，asOf 為來源出表日期。",
            "只保留代碼、名稱、來源類型與出表日期；未回傳公司聯絡資料或個人欄位。",
            "上櫃標的可搜尋，但目前平台行情與回測只支援 TWSE；目錄存在不代表該代碼已可回測。",
            "FUND 表示基金基本資料來源，未將所有基金推定為 ETF；名錄存在不保證行情完整或可回測。",
            "停牌、上市／下市生命週期與公司行動仍未驗證；此名錄不解除回測發布阻擋。"))
    }
    private fun rank(i: Instrument, q: String): Int {
        val code = i.code.lowercase(Locale.ROOT); val name = i.name.lowercase(Locale.ROOT)
        return when { code == q -> 0; code.startsWith(q) -> 1; code.contains(q) -> 2; name == q -> 3; name.startsWith(q) -> if (i.kind == "STOCK") 4 else 5; else -> 6 }
    }
}
