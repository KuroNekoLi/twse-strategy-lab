import type { DailyBar } from "./backtest-engine";

// Known unit split from the TWSE ETF announcement. Divide pre-event prices by
// the 4:1 ratio so share quantities and price continuity are on one basis.
// https://www.twse.com.tw/staticFiles/news/news/tsecnews/8a8216d696b406fc0196ce27c2e90063.pdf
const splitEvents: Record<string, Array<{ effectiveDate: string; ratio: number }>> = {
  "0050": [{ effectiveDate: "2025-06-18", ratio: 4 }],
};

export function adjustKnownSplits(symbol: string, bars: DailyBar[]): DailyBar[] {
  const events = splitEvents[symbol] ?? [];
  return bars.map((bar) => {
    const factor = events.reduce((result, event) => bar.date < event.effectiveDate ? result / event.ratio : result, 1);
    return { ...bar, close: bar.close * factor };
  });
}

export function splitAssumptions(symbol: string) {
  return (splitEvents[symbol] ?? []).map((event) => `${event.effectiveDate} 受益權分割已調整 ${event.ratio}:1`);
}
