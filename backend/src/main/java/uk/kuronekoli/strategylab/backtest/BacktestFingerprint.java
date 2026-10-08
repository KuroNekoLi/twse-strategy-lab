package uk.kuronekoli.strategylab.backtest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestResponse.DatasetManifest;
import uk.kuronekoli.strategylab.api.BacktestResponse.ResolvedConfig;
import uk.kuronekoli.strategylab.api.BacktestResponse.RunMetadata;
import uk.kuronekoli.strategylab.market.DailyBar;

/** Canonical UTF-8 LF format is independent of decimal presentation and map iteration. */
public final class BacktestFingerprint {
  private BacktestFingerprint() {}
  private static String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
  public static String dataset(String symbol, List<DailyBar> bars) {
    BacktestValidation.bars(bars);
    StringBuilder canonical = new StringBuilder("normalized-close-dataset-v1\n").append(symbol).append('\n');
    for (DailyBar bar : bars) canonical.append(bar.date()).append('|').append(decimal(bar.close())).append('\n');
    return sha256(canonical.toString());
  }
  public static RunMetadata metadata(ResolvedConfig c, List<DatasetManifest> datasets) {
    StringBuilder datasetIds = new StringBuilder("ordered-datasets-v1\n");
    for (DatasetManifest d : datasets) datasetIds.append(d.symbol()).append('|').append(d.sha256()).append('\n');
    String datasetHash = sha256(datasetIds.toString());
    StringBuilder config = new StringBuilder("resolved-config-v1\n");
    config.append(String.join(",", c.symbols())).append('|').append(c.from()).append('|').append(c.to()).append('|').append(c.effectiveTo()).append('|').append(c.strategy()).append('|')
        .append(c.fastWindow()).append('|').append(c.slowWindow()).append('|').append(c.rsiWindow()).append('|').append(c.rsiBuyThreshold()).append('|').append(c.rsiSellThreshold()).append('|')
        .append(c.bollingerWindow()).append('|').append(c.bollingerMultiplier()).append('|').append(c.breakoutWindow()).append('|').append(c.drawdownBuyPercent()).append('|').append(c.profitSellPercent()).append('|')
        .append(decimal(c.initialCapital())).append('|').append(decimal(c.monthlyContribution())).append('|').append(decimal(c.commissionRate())).append('|').append(decimal(c.sellTaxRate())).append('|').append(c.useMarketTaxDefaults()).append('\n');
    for (String symbol : c.symbols()) config.append(symbol).append('|').append(decimal(c.appliedSellTaxRates().get(symbol))).append('\n');
    String versions = String.join("|", BacktestEngine.VERSION, StrategySignals.VERSION, BacktestEngine.EXECUTION_VERSION, BacktestEngine.COST_VERSION, BacktestEngine.ADJUSTMENT, BacktestEngine.RULES_VERSION, BacktestEngine.METRICS_VERSION);
    String id = "bt_" + sha256(versions + "\n" + datasetHash + "\n" + config);
    return new RunMetadata(id, BacktestEngine.VERSION, StrategySignals.VERSION, BacktestEngine.EXECUTION_MODEL, BacktestEngine.EXECUTION_VERSION, BacktestEngine.COST_VERSION,
        BacktestEngine.ADJUSTMENT, BacktestEngine.RULES_VERSION, BacktestEngine.METRICS_VERSION, datasetHash, "IDENTIFIED_NOT_ARCHIVED", c, List.copyOf(datasets));
  }
  private static String sha256(String text) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
  }
}
