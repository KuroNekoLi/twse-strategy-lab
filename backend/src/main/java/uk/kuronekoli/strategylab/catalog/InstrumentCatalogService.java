package uk.kuronekoli.strategylab.catalog;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument;

@Service
public final class InstrumentCatalogService {
  private final InstrumentCatalogClient catalog;
  public InstrumentCatalogService(InstrumentCatalogClient catalog) { this.catalog = catalog; }
  public InstrumentCatalogResponse search(String query, int limit) {
    if (query == null || query.codePointCount(0, query.length()) > 60 || query.codePoints().anyMatch(Character::isISOControl)) throw CatalogException.input("請輸入最多 60 字的代碼或名稱。");
    String normalized = Normalizer.normalize(query.strip(), Normalizer.Form.NFKC);
    if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 60 || limit < 1 || limit > 50) throw CatalogException.input("請提供最多 60 字的代碼或名稱；每次查詢筆數需為 1 至 50。");
    String needle = normalized.toLowerCase(Locale.ROOT);
    var snapshots = catalog.snapshots();
    List<Instrument> all = snapshots.stream().flatMap(s -> s.rows().stream()).toList();
    Set<String> seen = new HashSet<>();
    for (Instrument item : all) if (!seen.add(item.code())) throw new CatalogException("CATALOG_CODE_CONFLICT", "不同名錄含相同代碼，已停止查詢以避免錯誤識別。", 502);
    List<Instrument> matches = all.stream().filter(i -> matches(i, needle)).sorted(Comparator.comparingInt((Instrument i) -> rank(i, needle)).thenComparing(Instrument::code)).toList();
    return new InstrumentCatalogResponse(normalized, limit, matches.size(), List.copyOf(matches.subList(0, Math.min(limit, matches.size()))),
        snapshots.stream().map(InstrumentCatalogClient.Snapshot::source).toList(), List.of(
            "來源公布更新頻率為每月；fetchedAt 只代表取得時間，asOf 為來源出表日期，快取不保證資料即時或完整。",
            "只保留代碼、名稱、來源類型與出表日期；未回傳公司聯絡資料或個人欄位。",
            "FUND 表示基金基本資料來源，未將所有基金推定為 ETF；名錄存在不保證本平台行情或回測可用。",
            "停牌、上市／下市生命週期與公司行動仍未驗證；此名錄不解除回測發布阻擋。"));
  }
  private boolean matches(Instrument i, String q) { return i.code().toLowerCase(Locale.ROOT).contains(q) || i.name().toLowerCase(Locale.ROOT).contains(q); }
  private int rank(Instrument i, String q) {
    String code = i.code().toLowerCase(Locale.ROOT), name = i.name().toLowerCase(Locale.ROOT);
    if (code.equals(q)) return 0; if (code.startsWith(q)) return 1; if (code.contains(q)) return 2; if (name.startsWith(q)) return 3; return 4;
  }
}
