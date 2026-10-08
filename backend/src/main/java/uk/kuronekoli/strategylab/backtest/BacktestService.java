package uk.kuronekoli.strategylab.backtest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import uk.kuronekoli.strategylab.api.ApiExceptionHandler.NoMarketDataException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.api.BacktestResponse;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.market.TwseMarketDataClient;

@Service
public class BacktestService {
  private static final Pattern SYMBOL = Pattern.compile("\\d{4,6}");
  private static final List<String> STRATEGIES = List.of("ma-crossover", "rsi-reversion", "bollinger-reversion", "breakout", "drawdown-entry");
  private final TwseMarketDataClient marketData;
  public BacktestService(TwseMarketDataClient marketData) { this.marketData = marketData; }

  public BacktestResponse run(BacktestRequest request) {
    List<String> symbols = new ArrayList<>(new LinkedHashSet<>((request.symbols() == null || request.symbols().isEmpty() ? List.of(request.symbol()) : request.symbols()).stream().map(String::trim).toList()));
    if (symbols.isEmpty() || symbols.size() > 3 || symbols.stream().anyMatch(code -> !SYMBOL.matcher(code).matches())) throw new IllegalArgumentException("請輸入 1 至 3 個 4 至 6 位數字的台股代碼。");
    LocalDate from = parseDate(request.from()), to = parseDate(request.to()), today = LocalDate.now(ZoneId.of("Asia/Taipei"));
    if (from.isBefore(LocalDate.of(2010, 1, 1)) || from.isAfter(to) || from.isAfter(today)) throw new IllegalArgumentException("回測日期格式不正確；此資料服務目前提供 2010 年起的行情。");
    LocalDate effectiveTo = to.isAfter(today) ? today : to;
    YearMonth firstMonth = YearMonth.from(from), lastMonth = YearMonth.from(effectiveTo);
    long months = java.time.temporal.ChronoUnit.MONTHS.between(firstMonth, lastMonth) + 1;
    if (months > 204) throw new IllegalArgumentException("單次回測最多 17 年，請縮短期間。");
    String strategy = request.strategyOrDefault();
    if (!STRATEGIES.contains(strategy)) throw new IllegalArgumentException("請選擇有效的策略範本。");
    validateParameters(request, strategy);

    List<BacktestResponse.AssetPeriod> assets = new ArrayList<>();
    List<BacktestResponse.StrategyResult> results = new ArrayList<>();
    for (String symbol : symbols) {
      List<DailyBar> bars = marketData.load(symbol, firstMonth, lastMonth).stream().filter(bar -> !bar.date().isBefore(from) && !bar.date().isAfter(effectiveTo)).map(bar -> adjustSplit(symbol, bar)).toList();
      if (bars.isEmpty()) throw new NoMarketDataException(symbol + " 在這段期間沒有找到可用的證交所日行情。");
      double tax = request.marketTaxDefaults() ? (symbol.matches("00\\d{2,4}") ? 0.001 : 0.003) : request.sellTaxRate().doubleValue();
      results.addAll(BacktestEngine.run(request, symbol, bars, tax));
      assets.add(new BacktestResponse.AssetPeriod(symbol, bars.get(0).date().toString(), bars.get(bars.size() - 1).date().toString(), bars.size()));
    }
    double commission = request.commissionRate().doubleValue() * 100;
    List<String> assumptions = new ArrayList<>(List.of("日收盤價", "不含配息", "月初投入", "買賣手續費 " + commission + "%"));
    if (request.marketTaxDefaults()) symbols.forEach(s -> assumptions.add(s + " 賣出交易稅 " + (s.matches("00\\d{2,4}") ? "0.1" : "0.3") + "%（依台股 ETF／股票預設分類估算）"));
    else assumptions.add("賣出交易稅 " + request.sellTaxRate().doubleValue() * 100 + "%");
    assumptions.addAll(List.of("未計券商最低手續費", "不含滑價", "訊號使用前一交易日資料，於當日收盤執行", "報酬以期末資產對總投入計算", "回撤依每日收盤估算"));
    if (symbols.contains("0050")) assumptions.add("0050 已依 2025-06-18 每 1 股分割為 4 股調整分割前收盤價");
    BacktestResponse.AssetPeriod primary = assets.get(0);
    return new BacktestResponse(symbols.get(0), symbols, primary.from(), primary.to(), primary.tradingDays(), assets, "臺灣證券交易所 STOCK_DAY", assumptions, results);
  }

  private LocalDate parseDate(String value) {
    try { return LocalDate.parse(value); } catch (Exception e) { throw new IllegalArgumentException("回測日期格式不正確；請使用 YYYY-MM-DD。"); }
  }
  private DailyBar adjustSplit(String symbol, DailyBar bar) {
    if (symbol.equals("0050") && bar.date().isBefore(LocalDate.of(2025, 6, 18))) return new DailyBar(bar.date(), bar.close() / 4);
    return bar;
  }
  private void validateParameters(BacktestRequest r, String strategy) {
    if (r.fastWindow() == null || r.slowWindow() == null || r.fastWindow() < 2 || r.slowWindow() > 500 || (strategy.equals("ma-crossover") && r.fastWindow() >= r.slowWindow())) throw new IllegalArgumentException("請確認均線日數，短均線需小於長均線。");
    if (!bounded(r.value(r.rsiWindow(), 14), 2, 100) || !bounded(r.value(r.rsiBuyThreshold(), 30), 1, 49) || !bounded(r.value(r.rsiSellThreshold(), 55), 51, 99) || !bounded(r.value(r.bollingerWindow(), 20), 2, 200) || !bounded(r.value(r.bollingerMultiplier(), 2), 0.5, 5) || !bounded(r.value(r.breakoutWindow(), 20), 2, 250) || !bounded(r.value(r.drawdownBuyPercent(), 20), 1, 80) || !bounded(r.value(r.profitSellPercent(), 20), 1, 200)) throw new IllegalArgumentException("策略參數超出可用範圍，請調整後再試。");
  }
  private boolean bounded(double value, double min, double max) { return Double.isFinite(value) && value >= min && value <= max; }
}
