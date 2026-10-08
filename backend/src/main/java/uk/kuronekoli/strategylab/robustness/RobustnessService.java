package uk.kuronekoli.strategylab.robustness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.kuronekoli.strategylab.api.ApiExceptionHandler.NoMarketDataException;
import uk.kuronekoli.strategylab.api.BacktestException;
import uk.kuronekoli.strategylab.api.BacktestRequest;
import uk.kuronekoli.strategylab.backtest.BacktestValidation;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.market.TwseMarketDataClient;

@Service
public class RobustnessService {
  private static final Pattern SYMBOL = Pattern.compile("\\d{4,6}");
  private static final List<String> LIMITATIONS = List.of(
      "LIMITED_RESEARCH：僅為明確列舉案例的歷史敏感度分析，不代表策略有效或預測未來。",
      "LICENSING_UNVERIFIED：行情授權與衍生結果再散布範圍尚未確認；不得據此公開發布或分享。",
      "DIVIDENDS_UNSUPPORTED：未計算股利、公司行動、滑價、最低手續費或交易日曆；成本為簡化假設。",
      "SNAPSHOT_NOT_ARCHIVED：僅在本次請求記憶體使用行情，不持久保存行情或分析結果；未保存快照，無法保證日後重播相同資料。");

  private final TwseMarketDataClient marketData;
  private final Clock clock;

  @Autowired
  public RobustnessService(TwseMarketDataClient marketData) {
    this(marketData, Clock.system(ZoneId.of("Asia/Taipei")));
  }

  RobustnessService(TwseMarketDataClient marketData, Clock clock) {
    this.marketData = marketData;
    this.clock = clock;
  }

  public RobustnessResponse run(RobustnessRequest request) {
    if (request == null || request.base() == null) throw BacktestException.input("請提供穩健性分析基準設定。");
    if (request.baseId() == null || !request.baseId().matches("[A-Za-z0-9_-]{1,40}"))
      throw BacktestException.input("基準識別碼需為 1 至 40 個英數字、底線或連字號。");
    if (request.variants() == null || request.variants().isEmpty() || request.variants().size() > RobustnessMatrix.MAX_CASES)
      throw BacktestException.input("穩健性矩陣需包含 1 至 " + RobustnessMatrix.MAX_CASES + " 個案例。");

    BacktestRequest base = request.base();
    BacktestValidation.parameters(base);
    if (base.symbol() == null || !SYMBOL.matcher(base.symbol().trim()).matches())
      throw BacktestException.input("請提供 4 至 6 位數字的單一台股標的代碼。");
    LocalDate from = parseDate(base.from()), requestedTo = parseDate(base.to());
    LocalDate today = LocalDate.now(clock);
    if (from.isBefore(LocalDate.of(2010, 1, 1)) || from.isAfter(requestedTo) || from.isAfter(today))
      throw BacktestException.input("回測日期範圍不正確；此資料服務目前提供 2010 年起的行情。");
    LocalDate effectiveTo = requestedTo.isAfter(today) ? today : requestedTo;
    YearMonth first = YearMonth.from(from), last = YearMonth.from(effectiveTo);
    if (ChronoUnit.MONTHS.between(first, last) + 1 > 204)
      throw BacktestException.input("單次穩健性分析最多 17 年，請縮短期間。");

    String symbol = base.symbol().trim();
    BigDecimal appliedTax = base.marketTaxDefaults()
        ? new BigDecimal(symbol.matches("00\\d{2,4}") ? "0.001" : "0.003") : base.sellTaxRate();
    RobustnessMatrix.validateVariants(base, appliedTax, request.variants());
    // Exactly one client load is shared by every matrix case.
    List<DailyBar> loaded = marketData.load(symbol, first, last);
    BacktestValidation.bars(loaded);
    List<DailyBar> bars = loaded.stream().filter(bar -> !bar.date().isBefore(from) && !bar.date().isAfter(effectiveTo)).toList();
    if (bars.isEmpty()) throw new NoMarketDataException(symbol + " 在這段期間沒有可用日行情；上市、停牌與日曆狀態未知。");
    RobustnessMatrix.Result result = RobustnessMatrix.run(base, symbol, bars, appliedTax, request.variants());
    return new RobustnessResponse(request.baseId(), symbol, from.toString(), requestedTo.toString(),
        bars.get(0).date().toString(), bars.get(bars.size() - 1).date().toString(), bars.size(),
        "LIMITED_RESEARCH", LIMITATIONS, result.cases(), result.aggregate());
  }

  private LocalDate parseDate(String value) {
    try { return LocalDate.parse(value); }
    catch (Exception e) { throw BacktestException.input("回測日期格式不正確；請使用 YYYY-MM-DD。"); }
  }
}
