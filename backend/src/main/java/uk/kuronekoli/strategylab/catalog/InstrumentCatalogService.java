package uk.kuronekoli.strategylab.catalog;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument;

@Service
public final class InstrumentCatalogService {
  private static final Logger log = LoggerFactory.getLogger(InstrumentCatalogService.class);
  private final InstrumentCatalogClient catalog;
  private final InstrumentCatalogStore store;
  public InstrumentCatalogService(InstrumentCatalogClient catalog) { this(catalog, new MemoryInstrumentCatalogStore()); }
  @Autowired
  public InstrumentCatalogService(InstrumentCatalogClient catalog, InstrumentCatalogStore store) { this.catalog = catalog; this.store = store; }

  /** Fetches and validates every source before atomically replacing the persisted snapshot. */
  public synchronized void refresh() {
    List<InstrumentCatalogClient.Snapshot> snapshots = catalog.snapshots();
    store.replace(snapshots);
    log.info("Refreshed instrument catalog: {} items from {} sources", snapshots.stream().mapToInt(s -> s.rows().size()).sum(), snapshots.size());
  }

  @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Taipei")
  public void refreshDaily() {
    try { refresh(); }
    catch (RuntimeException failure) { log.warn("Daily instrument catalog refresh failed; the last persisted catalog remains available"); }
  }

  public InstrumentCatalogResponse search(String query, int limit) {
    if (query == null || query.codePointCount(0, query.length()) > 60 || query.codePoints().anyMatch(Character::isISOControl)) throw CatalogException.input("請輸入最多 60 字的代碼或名稱。");
    String normalized = Normalizer.normalize(query.strip(), Normalizer.Form.NFKC);
    if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 60 || limit < 1 || limit > 50) throw CatalogException.input("請提供最多 60 字的代碼或名稱；每次查詢筆數需為 1 至 50。");
    String needle = normalized.toLowerCase(Locale.ROOT);
    if (store.isEmpty()) refresh();
    List<Instrument> matches = store.matching(needle);
    Set<String> seen = new HashSet<>();
    for (Instrument item : matches) if (!seen.add(item.code())) throw new CatalogException("CATALOG_CODE_CONFLICT", "不同市場名錄含相同代碼，已停止查詢以避免錯誤識別。", 502);
    matches = matches.stream().sorted(Comparator.comparingInt((Instrument i) -> rank(i, needle)).thenComparing(Instrument::code).thenComparing(Instrument::market)).toList();
    return new InstrumentCatalogResponse(normalized, limit, matches.size(), List.copyOf(matches.subList(0, Math.min(limit, matches.size()))),
        store.sources(), List.of(
            "名錄含上市公司、上市基金與上櫃公司；來源更新頻率不同，fetchedAt 代表本服務取得時間，asOf 為來源出表日期。",
            "只保留代碼、名稱、來源類型與出表日期；未回傳公司聯絡資料或個人欄位。",
            "上櫃標的可搜尋，但目前平台行情與回測只支援 TWSE；目錄存在不代表該代碼已可回測。",
            "FUND 表示基金基本資料來源，未將所有基金推定為 ETF；名錄存在不保證行情完整或可回測。",
            "停牌、上市／下市生命週期與公司行動仍未驗證；此名錄不解除回測發布阻擋。"));
  }
  private int rank(Instrument i, String q) {
    String code = i.code().toLowerCase(Locale.ROOT), name = i.name().toLowerCase(Locale.ROOT);
    if (code.equals(q)) return 0;
    if (code.startsWith(q)) return 1;
    if (code.contains(q)) return 2;
    if (name.equals(q)) return 3;
    if (name.startsWith(q)) return i.kind().equals("STOCK") ? 4 : 5;
    return 6;
  }
}
