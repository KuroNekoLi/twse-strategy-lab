export type DailyBar = { date: string; close: number };
export type StrategyId = "ma-crossover" | "rsi-reversion" | "bollinger-reversion" | "breakout" | "drawdown-entry";
export type BacktestInput = {
  symbol: string; symbols?: string[]; from: string; to: string; strategy?: StrategyId;
  fastWindow: number; slowWindow: number; rsiWindow?: number; rsiBuyThreshold?: number; rsiSellThreshold?: number;
  bollingerWindow?: number; bollingerMultiplier?: number; breakoutWindow?: number;
  drawdownBuyPercent?: number; profitSellPercent?: number;
  monthlyContribution: number; initialCapital: number; commissionRate: number; sellTaxRate: number; useMarketTaxDefaults?: boolean;
};
type Point = { date: string; value: number };
type Portfolio = { cash: number; shares: number; contributed: number; previousMonth: string; peak: number; maxDrawdown: number; points: Point[]; costBasis: number };

const strategyNames: Record<StrategyId, string> = {
  "ma-crossover": "雙均線交叉", "rsi-reversion": "RSI 均值回歸", "bollinger-reversion": "布林通道回歸", breakout: "區間突破", "drawdown-entry": "回跌買進 / 獲利賣出",
};
function makePortfolio(): Portfolio { return { cash: 0, shares: 0, contributed: 0, previousMonth: "", peak: 0, maxDrawdown: 0, points: [], costBasis: 0 }; }

function buy(p: Portfolio, price: number, feeRate: number) {
  if (p.cash <= 0 || price <= 0) return;
  const amount = p.cash;
  const units = amount / (price * (1 + feeRate));
  p.costBasis = (p.costBasis * p.shares + price * units) / (p.shares + units);
  p.cash = 0;
  p.shares += units;
}
function sell(p: Portfolio, price: number, feeRate: number, taxRate: number) {
  if (p.shares <= 0) return;
  p.cash += p.shares * price * (1 - feeRate - taxRate);
  p.shares = 0;
  p.costBasis = 0;
}
function average(values: number[]) { return values.reduce((sum, value) => sum + value, 0) / values.length; }
function rsi(values: number[]) {
  const changes = values.slice(1).map((value, index) => value - values[index]);
  const gains = changes.map((value) => Math.max(value, 0));
  const losses = changes.map((value) => Math.max(-value, 0));
  const avgGain = average(gains), avgLoss = average(losses);
  if (avgLoss === 0) return avgGain === 0 ? 50 : 100;
  return 100 - (100 / (1 + avgGain / avgLoss));
}
function finalize(key: string, name: string, p: Portfolio, currentValue: number, firstDate: string, lastDate: string) {
  const elapsedYears = Math.max((new Date(lastDate).getTime() - new Date(firstDate).getTime()) / (365.2425 * 86400000), 1 / 365.2425);
  const totalReturn = p.contributed > 0 ? ((currentValue / p.contributed) - 1) * 100 : 0;
  const annualizedReturn = currentValue > 0 && p.contributed > 0 ? ((currentValue / p.contributed) ** (1 / elapsedYears) - 1) * 100 : -100;
  return { key, name, endingValue: Math.round(currentValue), contributed: Math.round(p.contributed), totalReturn, annualizedReturn, maxDrawdown: p.maxDrawdown * 100, series: p.points };
}

export function strategyLabel(input: BacktestInput) {
  const strategy = input.strategy ?? "ma-crossover";
  if (strategy === "ma-crossover") return `${strategyNames[strategy]} · ${input.fastWindow}/${input.slowWindow} 日`;
  if (strategy === "rsi-reversion") return `${strategyNames[strategy]} · RSI${input.rsiWindow ?? 14} < ${input.rsiBuyThreshold ?? 30} 買 / > ${input.rsiSellThreshold ?? 55} 賣`;
  if (strategy === "bollinger-reversion") return `${strategyNames[strategy]} · ${input.bollingerWindow ?? 20} 日 / ${input.bollingerMultiplier ?? 2}σ`;
  if (strategy === "breakout") return `${strategyNames[strategy]} · 突破 ${input.breakoutWindow ?? 20} 日高點`;
  return `${strategyNames[strategy]} · 回跌 ${input.drawdownBuyPercent ?? 20}% 買 / 獲利 ${input.profitSellPercent ?? 20}% 賣`;
}

export function runBacktest(input: BacktestInput, bars: DailyBar[]) {
  const strategy = input.strategy ?? "ma-crossover";
  const lookback = strategy === "drawdown-entry" ? 252
    : strategy === "breakout" ? (input.breakoutWindow ?? 20) + 1
      : strategy === "rsi-reversion" ? (input.rsiWindow ?? 14) + 1
        : strategy === "bollinger-reversion" ? input.bollingerWindow ?? 20
          : input.slowWindow;
  if (bars.length < lookback + 2) throw new Error(`此期間只有 ${bars.length} 個交易日，資料不足以計算所選策略。`);
  const dca = makePortfolio(), active = makePortfolio();
  const seriesStride = Math.max(1, Math.floor(bars.length / 320));

  bars.forEach((bar, index) => {
    const month = bar.date.slice(0, 7);
    const isNewMonth = month !== dca.previousMonth;
    if (index === 0) {
      dca.cash += input.initialCapital; dca.contributed += input.initialCapital;
      active.cash += input.initialCapital; active.contributed += input.initialCapital;
    } else if (isNewMonth) {
      dca.cash += input.monthlyContribution; dca.contributed += input.monthlyContribution;
      active.cash += input.monthlyContribution; active.contributed += input.monthlyContribution;
    }
    dca.previousMonth = month;
    if (index === 0 || isNewMonth) buy(dca, bar.close, input.commissionRate);

    // Every signal is computed from completed bars through yesterday; orders fill at today's close.
    const history = bars.slice(Math.max(0, index - lookback - 1), index).map((item) => item.close);
    if (history.length >= lookback) {
      const previous = history.at(-1)!;
      let shouldBuy = false, shouldSell = false;
      if (strategy === "ma-crossover") {
        const fastAverage = average(history.slice(-input.fastWindow));
        const slowAverage = average(history.slice(-input.slowWindow));
        shouldBuy = fastAverage > slowAverage;
        shouldSell = fastAverage <= slowAverage;
      } else if (strategy === "rsi-reversion") {
        const value = rsi(history.slice(-(input.rsiWindow ?? 14) - 1));
        shouldBuy = value < (input.rsiBuyThreshold ?? 30);
        shouldSell = value > (input.rsiSellThreshold ?? 55);
      } else if (strategy === "bollinger-reversion") {
        const window = history.slice(-(input.bollingerWindow ?? 20));
        const mean = average(window);
        const deviation = Math.sqrt(average(window.map((value) => (value - mean) ** 2)));
        const lower = mean - deviation * (input.bollingerMultiplier ?? 2);
        shouldBuy = previous < lower;
        shouldSell = previous >= mean;
      } else if (strategy === "breakout") {
        const window = history.slice(-((input.breakoutWindow ?? 20) + 1));
        const priorHigh = Math.max(...window.slice(0, -1));
        shouldBuy = previous > priorHigh;
        shouldSell = previous < average(window);
      } else {
        const window = history.slice(-Math.min(lookback, 252));
        const recentHigh = Math.max(...window);
        shouldBuy = previous <= recentHigh * (1 - (input.drawdownBuyPercent ?? 20) / 100);
        shouldSell = active.costBasis > 0 && previous >= active.costBasis * (1 + (input.profitSellPercent ?? 20) / 100);
      }
      if (active.shares > 0 && shouldSell) sell(active, bar.close, input.commissionRate, input.sellTaxRate);
      else if (active.shares === 0 && shouldBuy) buy(active, bar.close, input.commissionRate);
    }

    const dcaValue = dca.cash + dca.shares * bar.close;
    const activeValue = active.cash + active.shares * bar.close;
    for (const [portfolio, value] of [[dca, dcaValue], [active, activeValue]] as const) {
      portfolio.peak = Math.max(portfolio.peak, value);
      if (portfolio.peak > 0) portfolio.maxDrawdown = Math.max(portfolio.maxDrawdown, (portfolio.peak - value) / portfolio.peak);
      if (index % seriesStride === 0 || index === bars.length - 1) portfolio.points.push({ date: month, value: Math.round(value) });
    }
  });

  const last = bars.at(-1)!;
  return [
    finalize(`${input.symbol}-${strategy}`, `${input.symbol} · ${strategyLabel(input)}`, active, active.cash + active.shares * last.close, bars[0].date, last.date),
    finalize(`${input.symbol}-dca`, `${input.symbol} · 定期定額`, dca, dca.cash + dca.shares * last.close, bars[0].date, last.date),
  ];
}
