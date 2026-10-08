package uk.kuronekoli.strategylab.backtest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import uk.kuronekoli.strategylab.api.ApiExceptionHandler.NoMarketDataException;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.api.BacktestResponse;
import uk.kuronekoli.strategylab.api.BacktestResponse.*;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.market.TwseMarketDataClient;

@Service
public class BacktestService {
  private static final Pattern SYMBOL = Pattern.compile("\\d{4,6}");
  private final TwseMarketDataClient marketData;
  private final Clock clock;
  @Autowired
  public BacktestService(TwseMarketDataClient marketData) { this(marketData, Clock.system(ZoneId.of("Asia/Taipei"))); }
  BacktestService(TwseMarketDataClient marketData, Clock clock) { this.marketData = marketData; this.clock = clock; }

  public BacktestResponse run(BacktestRequest request) {
    BacktestValidation.parameters(request);
    List<String> symbols = symbols(request);
    LocalDate from = parseDate(request.from()), to = parseDate(request.to()), today = LocalDate.now(clock);
    if (from.isBefore(LocalDate.of(2010, 1, 1)) || from.isAfter(to) || from.isAfter(today)) throw BacktestException.input("回測日期範圍不正確；此資料服務目前提供 2010 年起的行情。");
    LocalDate effectiveTo = to.isAfter(today) ? today : to;
    YearMonth firstMonth = YearMonth.from(from), lastMonth = YearMonth.from(effectiveTo);
    if (java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1 > 204) throw BacktestException.input("單次回測最多 17 年，請縮短期間。");
    List<AssetPeriod> assets = new ArrayList<>();
    List<StrategyResult> results = new ArrayList<>();
    List<DatasetManifest> datasets = new ArrayList<>();
    Map<String, BigDecimal> taxes = new LinkedHashMap<>();
    for (String symbol : symbols) {
      BigDecimal tax = request.marketTaxDefaults() ? new BigDecimal(symbol.matches("00\\d{2,4}") ? "0.001" : "0.003") : request.sellTaxRate();
      BacktestValidation.costs(request.commissionRate(), tax); taxes.put(symbol, tax);
      List<DailyBar> loaded = marketData.load(symbol, firstMonth, lastMonth);
      BacktestValidation.bars(loaded);
      List<DailyBar> bars = loaded.stream().filter(bar -> !bar.date().isBefore(from) && !bar.date().isAfter(effectiveTo)).toList();
      if (bars.isEmpty()) throw new NoMarketDataException(symbol + " 在這段期間沒有可用日行情；上市、停牌與日曆狀態未知。");
      results.addAll(BacktestEngine.run(request, symbol, bars, tax));
      String first = bars.get(0).date().toString(), last = bars.get(bars.size() - 1).date().toString();
      assets.add(new AssetPeriod(symbol, first, last, bars.size(), request.from(), request.to(), "UNVERIFIED_CALENDAR", "UNKNOWN"));
      datasets.add(new DatasetManifest(symbol, BacktestFingerprint.dataset(symbol, bars), bars.size(), first, last, "UNKNOWN", "UNSUPPORTED", "UNSUPPORTED"));
    }
    List<Limitation> limitations = new ArrayList<>(List.of(
        new Limitation("HYPOTHETICAL_EXECUTION", "NEXT_CLOSE_PROXY：第 T 日收盤後訊號，使用下一筆觀察資料的收盤價代理第 T+1 日，無法保證真實成交。"),
        new Limitation("DIVIDENDS_UNSUPPORTED", "未取得股利資料；未計算含息總報酬。"),
        new Limitation("CORPORATE_ACTIONS_UNSUPPORTED", "未處理分割、除權與其他公司行動；使用未調整原始收盤價。"),
        new Limitation("CALENDAR_STATUS_UNKNOWN", "未接入交易日曆與商品生命週期；缺漏、停牌、尚未上市及下市狀態未知。"),
        new Limitation("LICENSING_UNVERIFIED", "行情授權與再散布範圍尚未確認；不得據此公開發布。"),
        new Limitation("SNAPSHOT_NOT_ARCHIVED", "雜湊只識別本次使用資料，未保存原始行情快照，無法保證未來重新取得相同資料。"),
        new Limitation("COSTS_SIMPLIFIED", "無最低手續費、券商折扣、滑價、交易量或價格限制模擬；整數股不代表已驗證零股成交。")));
    if (request.marketTaxDefaults()) limitations.add(new Limitation("TAX_CLASSIFICATION_ESTIMATE", "交易稅以代碼前綴 00 分類估算（ETF 0.1%、其餘 0.3%），未驗證歷史商品類型及稅率。"));
    if (to.isAfter(today)) limitations.add(new Limitation("FUTURE_END_CLAMPED", "要求迄日 " + to + " 超過今日，計算查詢上限為 " + effectiveTo + "；實際資料迄日另列。"));
    for (AssetPeriod asset : assets) if (!asset.from().equals(request.from()) || !asset.to().equals(request.to()))
      limitations.add(new Limitation("COVERAGE_DIFFERS_FROM_REQUEST", asset.symbol() + " 要求 " + request.from() + " 至 " + request.to() + "，觀察到 " + asset.from() + " 至 " + asset.to() + "；無日曆不能確認邊界缺漏原因。"));
    List<String> assumptions = List.of("日收盤價；NEXT_CLOSE_PROXY 假設研究模型", "策略 T 收盤訊號 / 下一筆觀察資料收盤代理價", "DCA 首筆及每月第一筆觀察資料投入，於該筆收盤代理價買進", "金額與費用：小數二位 HALF_UP；整數股及剩餘現金", "均價包含買入手續費；不加碼的策略持倉全部賣出", "totalReturn：期末損益／總投入；timeWeightedReturn：每日排除外部投入之 TWR", "annualizedReturn：TWR 依實際日數／365.2425 年化；maxDrawdown：TWR 正規化資產最大回撤", "股利、公司行動、交易日曆與停牌未支援；發布閘門 BLOCKED");
    ResolvedConfig config = new ResolvedConfig(List.copyOf(symbols), request.from(), request.to(), effectiveTo.toString(), request.strategyOrDefault(),
        request.fastWindow(), request.slowWindow(), request.value(request.rsiWindow(), 14), request.value(request.rsiBuyThreshold(), 30), request.value(request.rsiSellThreshold(), 55),
        request.value(request.bollingerWindow(), 20), request.value(request.bollingerMultiplier(), 2), request.value(request.breakoutWindow(), 20), request.value(request.drawdownBuyPercent(), 20), request.value(request.profitSellPercent(), 20),
        AccountingPortfolio.cents(request.initialCapital()), AccountingPortfolio.cents(request.monthlyContribution()), request.commissionRate(), request.sellTaxRate(), request.marketTaxDefaults(), java.util.Collections.unmodifiableMap(new LinkedHashMap<>(taxes)));
    RunMetadata metadata = BacktestFingerprint.metadata(config, datasets);
    AssetPeriod primary = assets.get(0);
    return new BacktestResponse(symbols.get(0), List.copyOf(symbols), primary.from(), primary.to(), primary.tradingDays(), List.copyOf(assets), "臺灣證券交易所 STOCK_DAY", assumptions, List.copyOf(results),
        request.from(), request.to(), "LIMITED_RESEARCH", "BLOCKED", List.copyOf(limitations), metadata);
  }
  private List<String> symbols(BacktestRequest r) {
    if (r.symbol() == null || !SYMBOL.matcher(r.symbol().trim()).matches()) throw BacktestException.input("請提供 4 至 6 位數字的主標的代碼。");
    List<String> raw = r.symbols() == null || r.symbols().isEmpty() ? List.of(r.symbol()) : r.symbols();
    if (raw.size() > 3 || raw.stream().anyMatch(s -> s == null || !SYMBOL.matcher(s.trim()).matches())) throw BacktestException.input("請輸入 1 至 3 個 4 至 6 位數字的台股代碼。");
    List<String> symbols = new ArrayList<>(new LinkedHashSet<>(raw.stream().map(String::trim).toList()));
    if (symbols.isEmpty()) throw BacktestException.input("請提供回測標的。");
    return symbols;
  }
  private LocalDate parseDate(String value) {
    try { return LocalDate.parse(value); } catch (Exception e) { throw BacktestException.input("回測日期格式不正確；請使用 YYYY-MM-DD。"); }
  }
}
