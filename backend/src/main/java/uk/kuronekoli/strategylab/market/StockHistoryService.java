package uk.kuronekoli.strategylab.market;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.backtest.BacktestValidation;

@Service
public class StockHistoryService {
  private static final Pattern SYMBOL = Pattern.compile("\\d{4,6}");
  private final TwseMarketDataClient marketData;
  private final Clock clock;
  private final boolean enabled;

  @Autowired
  public StockHistoryService(TwseMarketDataClient marketData, @Value("${app.market-history.enabled:false}") boolean enabled) {
    this(marketData, Clock.system(ZoneId.of("Asia/Taipei")), enabled);
  }

  StockHistoryService(TwseMarketDataClient marketData, Clock clock, boolean enabled) {
    this.marketData = marketData;
    this.clock = clock;
    this.enabled = enabled;
  }

  public StockHistoryResponse history(String symbol, String fromText, String toText) {
    if (!enabled) throw new BacktestException("MARKET_HISTORY_DISABLED", "市場資料圖表目前未在此環境啟用；行情使用權確認前不對外提供。", 503);
    if (symbol == null || !SYMBOL.matcher(symbol).matches()) throw BacktestException.input("請提供 4 至 6 位數字的台股代碼。");
    LocalDate from = parseDate(fromText), to = parseDate(toText), today = LocalDate.now(clock);
    if (from.isBefore(LocalDate.of(2010, 1, 1)) || from.isAfter(to) || to.isAfter(today)
        || from.plusYears(5).isBefore(to)) {
      throw BacktestException.input("請選擇 2010 年起、截至今日且不超過 5 年的日期範圍。");
    }
    List<DailyBar> loaded = marketData.load(symbol, YearMonth.from(from), YearMonth.from(to));
    BacktestValidation.bars(loaded);
    List<DailyBar> bars = loaded.stream().filter(bar -> !bar.date().isBefore(from) && !bar.date().isAfter(to)).toList();
    if (bars.isEmpty()) throw new BacktestException("NO_MARKET_DATA", symbol + " 在此日期範圍沒有可用收盤資料。", 404);
    return new StockHistoryResponse(symbol, from.toString(), to.toString(), bars.get(0).date().toString(),
        bars.get(bars.size() - 1).date().toString(), java.time.OffsetDateTime.now(clock).toString(),
        "TWSE STOCK_DAY（月歷史端點）", "1d", "UNKNOWN", "未調整原始收盤價",
        bars.stream().map(bar -> new StockHistoryResponse.Bar(bar.date().toString(), bar.close().toPlainString())).toList(),
        List.of("目前只呈現每日收盤價；無 OHLC、成交量、股利或公司行動資料。",
            "行情授權、歷史完整性與衍生圖表展示權尚未確認；公開發布狀態 BLOCKED。",
            "資料缺漏與停牌原因未知；資料不代表即時行情。"));
  }

  private LocalDate parseDate(String value) {
    try { return LocalDate.parse(value); }
    catch (RuntimeException error) { throw BacktestException.input("日期格式不正確；請使用 YYYY-MM-DD。"); }
  }
}
