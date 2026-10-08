export type DailyBar = { date: string; close: number };
export type BacktestInput = {
  symbol: string; from: string; to: string; fastWindow: number; slowWindow: number;
  monthlyContribution: number; initialCapital: number; commissionRate: number; sellTaxRate: number;
};
type Point = { date: string; value: number };

type Portfolio = { cash: number; shares: number; contributed: number; previousMonth: string; peak: number; maxDrawdown: number; points: Point[] };

function makePortfolio(): Portfolio { return { cash: 0, shares: 0, contributed: 0, previousMonth: "", peak: 0, maxDrawdown: 0, points: [] }; }

function buy(portfolio: Portfolio, price: number, feeRate: number) {
  if (portfolio.cash <= 0 || price <= 0) return;
  const units = portfolio.cash / (price * (1 + feeRate));
  portfolio.cash = 0;
  portfolio.shares += units;
}

function sell(portfolio: Portfolio, price: number, feeRate: number, taxRate: number) {
  if (portfolio.shares <= 0) return;
  const gross = portfolio.shares * price;
  portfolio.cash += gross * (1 - feeRate - taxRate);
  portfolio.shares = 0;
}

function finalize(key: string, name: string, p: Portfolio, currentValue: number, firstDate: string, lastDate: string) {
  const elapsedYears = Math.max((new Date(lastDate).getTime() - new Date(firstDate).getTime()) / (365.2425 * 86400000), 1 / 365.2425);
  const totalReturn = p.contributed > 0 ? ((currentValue / p.contributed) - 1) * 100 : 0;
  const annualizedReturn = currentValue > 0 && p.contributed > 0 ? ((currentValue / p.contributed) ** (1 / elapsedYears) - 1) * 100 : -100;
  return { key, name, endingValue: Math.round(currentValue), contributed: Math.round(p.contributed), totalReturn, annualizedReturn, maxDrawdown: p.maxDrawdown * 100, series: p.points };
}

export function runBacktest(input: BacktestInput, bars: DailyBar[]) {
  if (bars.length < input.slowWindow + 2) throw new Error(`此期間只有 ${bars.length} 個交易日，資料不足以計算 ${input.slowWindow} 日均線。`);
  const dca = makePortfolio();
  const ma = makePortfolio();
  const monthlyAmount = input.monthlyContribution;
  const seriesStride = Math.max(1, Math.floor(bars.length / 320));

  bars.forEach((bar, index) => {
    const month = bar.date.slice(0, 7);
    const isNewMonth = month !== dca.previousMonth;
    if (index === 0) {
      dca.cash += input.initialCapital; dca.contributed += input.initialCapital;
      ma.cash += input.initialCapital; ma.contributed += input.initialCapital;
    } else if (isNewMonth) {
      dca.cash += monthlyAmount; dca.contributed += monthlyAmount;
      ma.cash += monthlyAmount; ma.contributed += monthlyAmount;
    }
    dca.previousMonth = month; ma.previousMonth = month;

    if (index === 0 || isNewMonth) buy(dca, bar.close, input.commissionRate);

    // Signals use the previous close only; today's close is used only to execute the order.
    if (index >= input.slowWindow) {
      const history = bars.slice(index - input.slowWindow, index).map((item) => item.close);
      const fastAverage = history.slice(-input.fastWindow).reduce((sum, value) => sum + value, 0) / input.fastWindow;
      const slowAverage = history.reduce((sum, value) => sum + value, 0) / input.slowWindow;
      if (fastAverage > slowAverage && ma.shares === 0) buy(ma, bar.close, input.commissionRate);
      if (fastAverage <= slowAverage && ma.shares > 0) sell(ma, bar.close, input.commissionRate, input.sellTaxRate);
    }

    const dcaValue = dca.cash + dca.shares * bar.close;
    const maValue = ma.cash + ma.shares * bar.close;
    for (const [portfolio, value] of [[dca, dcaValue], [ma, maValue]] as const) {
      portfolio.peak = Math.max(portfolio.peak, value);
      if (portfolio.peak > 0) portfolio.maxDrawdown = Math.max(portfolio.maxDrawdown, (portfolio.peak - value) / portfolio.peak);
      if (index % seriesStride === 0 || index === bars.length - 1) portfolio.points.push({ date: month, value: Math.round(value) });
    }
  });

  const last = bars.at(-1)!;
  return [
    finalize("moving-average", `${input.fastWindow}/${input.slowWindow} 日均線`, ma, ma.cash + ma.shares * last.close, bars[0].date, last.date),
    finalize("dca", "定期定額", dca, dca.cash + dca.shares * last.close, bars[0].date, last.date),
  ];
}
