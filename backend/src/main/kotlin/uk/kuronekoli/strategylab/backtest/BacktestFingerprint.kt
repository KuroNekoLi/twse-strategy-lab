package uk.kuronekoli.strategylab.backtest

import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat
import uk.kuronekoli.strategylab.api.BacktestResponse.DatasetManifest
import uk.kuronekoli.strategylab.api.BacktestResponse.ResolvedConfig
import uk.kuronekoli.strategylab.api.BacktestResponse.RunMetadata
import uk.kuronekoli.strategylab.market.DailyBar

/** Canonical UTF-8 LF format is independent of decimal presentation and map iteration. */
object BacktestFingerprint {
  private fun decimal(value: BigDecimal) = value.stripTrailingZeros().toPlainString()

  fun dataset(symbol: String, bars: List<DailyBar>): String {
    BacktestValidation.bars(bars)
    val canonical = StringBuilder("normalized-close-dataset-v1\n").append(symbol).append('\n')
    for (bar in bars) canonical.append(bar.date).append('|').append(decimal(bar.close)).append('\n')
    return sha256(canonical.toString())
  }

  fun metadata(c: ResolvedConfig, datasets: List<DatasetManifest>): RunMetadata {
    val datasetIds = StringBuilder("ordered-datasets-v1\n")
    for (d in datasets) datasetIds.append(d.symbol).append('|').append(d.sha256).append('\n')
    val datasetHash = sha256(datasetIds.toString())
    val config = StringBuilder("resolved-config-v1\n")
    config.append(c.symbols.joinToString(",")).append('|').append(c.from).append('|').append(c.to).append('|').append(c.effectiveTo).append('|').append(c.strategy).append('|')
      .append(c.fastWindow).append('|').append(c.slowWindow).append('|').append(c.rsiWindow).append('|').append(c.rsiBuyThreshold).append('|').append(c.rsiSellThreshold).append('|')
      .append(c.bollingerWindow).append('|').append(c.bollingerMultiplier).append('|').append(c.breakoutWindow).append('|').append(c.drawdownBuyPercent).append('|').append(c.profitSellPercent).append('|')
      .append(decimal(c.initialCapital)).append('|').append(decimal(c.monthlyContribution)).append('|').append(decimal(c.commissionRate)).append('|').append(decimal(c.sellTaxRate)).append('|').append(c.useMarketTaxDefaults).append('\n')
    for (symbol in c.symbols) config.append(symbol).append('|').append(decimal(c.appliedSellTaxRates.getValue(symbol))).append('\n')
    val versions = listOf(BacktestEngine.VERSION, StrategySignals.VERSION, BacktestEngine.EXECUTION_VERSION, BacktestEngine.COST_VERSION,
      BacktestEngine.ADJUSTMENT, BacktestEngine.RULES_VERSION, BacktestEngine.METRICS_VERSION).joinToString("|")
    val id = "bt_${sha256("$versions\n$datasetHash\n$config")}"
    return RunMetadata(id, BacktestEngine.VERSION, StrategySignals.VERSION, BacktestEngine.EXECUTION_MODEL, BacktestEngine.EXECUTION_VERSION,
      BacktestEngine.COST_VERSION, BacktestEngine.ADJUSTMENT, BacktestEngine.RULES_VERSION, BacktestEngine.METRICS_VERSION,
      datasetHash, "IDENTIFIED_NOT_ARCHIVED", c, datasets.toList())
  }

  private fun sha256(text: String): String = try {
    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)))
  } catch (e: java.security.NoSuchAlgorithmException) {
    throw IllegalStateException("SHA-256 unavailable", e)
  }
}
