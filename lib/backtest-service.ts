import { runBacktest, type BacktestInput, type DailyBar } from "./backtest-engine";
import { adjustKnownSplits, splitAssumptions } from "./corporate-actions";

type TwseMonth = { stat?: string; data?: string[][] };
const symbols = new Set(["0050", "0056", "2330"]);
const monthKey = (year: number, month: number) => `${year}-${String(month).padStart(2, "0")}`;

function monthsBetween(from: string, to: string) {
  const [fromYear, fromMonth] = from.split("-").map(Number);
  const [toYear, toMonth] = to.split("-").map(Number);
  const output: string[] = [];
  for (let year = fromYear, month = fromMonth; year < toYear || (year === toYear && month <= toMonth);) {
    output.push(monthKey(year, month)); month++;
    if (month > 12) { month = 1; year++; }
  }
  return output;
}

function parseTwseDate(value: string) {
  const [year, month, day] = value.split("/").map(Number);
  return `${year < 1911 ? year + 1911 : year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

function toBars(body: TwseMonth): DailyBar[] {
  return (body.data ?? []).flatMap((row) => {
    const close = Number(row[6]?.replaceAll(",", ""));
    return row[0] && Number.isFinite(close) && close > 0 ? [{ date: parseTwseDate(row[0]), close }] : [];
  });
}

async function loadMonth(symbol: string, month: string): Promise<DailyBar[]> {
  const [year, monthNumber] = month.split("-").map(Number);
  const url = new URL("https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY");
  url.searchParams.set("date", `${year}${String(monthNumber).padStart(2, "0")}01`);
  url.searchParams.set("stockNo", symbol);
  url.searchParams.set("response", "json");
  const request = new Request(url.toString(), { headers: {
    Accept: "application/json, text/plain, */*",
    "Accept-Language": "zh-TW,zh;q=0.9,en;q=0.8",
    Referer: "https://www.twse.com.tw/",
    "User-Agent": "Mozilla/5.0 (compatible; TWSEStrategyLab/1.0)",
  } });
  // Sites Workers do not grant access to the account-wide default Cache API.
  // Fetch the public TWSE endpoint directly instead of touching caches.default.
  const response = await fetchTwse(request);
  if (!response.ok) throw new Error(`證交所行情服務回應 ${response.status}（${month}）。`);
  const body = await response.json() as TwseMonth;
  if (body.stat !== "OK") {
    if (!body.stat || /查詢日期小於|查詢日期大於|查無資料|無符合/.test(body.stat)) return [];
    throw new Error(`證交所暫時無法提供 ${month} 的行情：${body.stat}`);
  }
  return toBars(body);
}

async function fetchTwse(request: Request) {
  for (let attempt = 0; attempt < 3; attempt++) {
    let current = request;
    let retry = false;
    for (let hop = 0; hop < 4; hop++) {
      const response = await fetch(current, { redirect: "manual", signal: AbortSignal.timeout(15000) });
      if (![301, 302, 303, 307, 308].includes(response.status)) return response;
      const location = response.headers.get("location");
      if (!location) {
        if ([307, 429, 503].includes(response.status) && attempt < 2) {
          await response.body?.cancel();
          retry = true;
          break;
        }
        return response;
      }
      const nextUrl = new URL(location, current.url);
      if (nextUrl.protocol !== "https:" || !(nextUrl.hostname === "twse.com.tw" || nextUrl.hostname.endsWith(".twse.com.tw"))) {
        throw new Error("證交所行情服務導向了非證交所網址，已停止請求。");
      }
      current = new Request(nextUrl, { headers: request.headers });
    }
    if (!retry) throw new Error("證交所行情服務轉址次數過多，請稍後再試。");
    await new Promise((resolve) => setTimeout(resolve, 300 * (attempt + 1)));
  }
  throw new Error("證交所行情服務暫時拒絕請求，請稍後再試。");
}

async function loadBars(symbol: string, months: string[]) {
  const rows: DailyBar[] = [];
  for (let i = 0; i < months.length; i += 3) {
    const group = await Promise.all(months.slice(i, i + 3).map((month) => loadMonth(symbol, month)));
    rows.push(...group.flat());
    if (i + 3 < months.length) await new Promise((resolve) => setTimeout(resolve, 120));
  }
  return rows.sort((a, b) => a.date.localeCompare(b.date));
}

export async function handleBacktest(input: BacktestInput) {
  try {
    const selectedSymbols = [...new Set(input.symbols?.length ? input.symbols : [input.symbol])];
    if (!selectedSymbols.length || selectedSymbols.length > 3 || selectedSymbols.some((symbol) => !symbols.has(symbol))) return { status: 400, body: { error: "請選擇 1 至 3 檔示範標的：0050、0056 或 2330。" } };
    const today = new Date().toISOString().slice(0, 10);
    if (!/^2010-\d\d-\d\d$/.test(input.from) || !/^\d{4}-\d\d-\d\d$/.test(input.to) || input.from > input.to || input.from > today) return { status: 400, body: { error: "回測日期格式不正確；此資料服務目前提供 2010 年起的行情。" } };
    const effectiveTo = input.to > today ? today : input.to;
    const months = monthsBetween(input.from.slice(0, 7), effectiveTo.slice(0, 7));
    if (months.length > 204) return { status: 400, body: { error: "單次回測最多 17 年，請縮短期間。" } };
    const strategy = input.strategy ?? "ma-crossover";
    if (!["ma-crossover", "rsi-reversion", "bollinger-reversion", "breakout", "drawdown-entry"].includes(strategy)) return { status: 400, body: { error: "請選擇有效的策略範本。" } };
    if (!Number.isInteger(input.fastWindow) || !Number.isInteger(input.slowWindow) || input.fastWindow < 2 || (strategy === "ma-crossover" && input.fastWindow >= input.slowWindow) || input.slowWindow > 500) return { status: 400, body: { error: "請確認均線日數，短均線需小於長均線。" } };
    const bounded = (value: number | undefined, min: number, max: number, fallback: number) => Number.isFinite(value ?? fallback) && (value ?? fallback) >= min && (value ?? fallback) <= max;
    if (!bounded(input.rsiWindow, 2, 100, 14) || !bounded(input.rsiBuyThreshold, 1, 49, 30) || !bounded(input.rsiSellThreshold, 51, 99, 55) || !bounded(input.bollingerWindow, 2, 200, 20) || !bounded(input.bollingerMultiplier, 0.5, 5, 2) || !bounded(input.breakoutWindow, 2, 250, 20) || !bounded(input.drawdownBuyPercent, 1, 80, 20) || !bounded(input.profitSellPercent, 1, 200, 20)) return { status: 400, body: { error: "策略參數超出可用範圍，請調整後再試。" } };
    if (![input.monthlyContribution, input.initialCapital, input.commissionRate, input.sellTaxRate].every((value) => Number.isFinite(value) && value >= 0) || input.initialCapital <= 0) return { status: 400, body: { error: "投入金額和交易成本需為有效的非負數值。" } };

    const assets: Array<{ symbol: string; from: string; to: string; tradingDays: number }> = [];
    const results = [];
    for (const symbol of selectedSymbols) {
      const rawBars = (await loadBars(symbol, months)).filter((bar) => bar.date >= input.from && bar.date <= effectiveTo);
      if (!rawBars.length) return { status: 404, body: { error: `${symbol} 在這段期間沒有找到可用的證交所日行情。` } };
      const bars = adjustKnownSplits(symbol, rawBars);
      const sellTaxRate = input.useMarketTaxDefaults ? (symbol === "2330" ? 0.003 : 0.001) : input.sellTaxRate;
      results.push(...runBacktest({ ...input, symbol, sellTaxRate }, bars));
      assets.push({ symbol, from: bars[0].date, to: bars.at(-1)!.date, tradingDays: bars.length });
    }
    return { status: 200, body: {
      symbol: selectedSymbols[0], symbols: selectedSymbols, from: assets[0].from, to: assets[0].to, tradingDays: assets[0].tradingDays, assets,
      dataSource: "臺灣證券交易所 STOCK_DAY", assumptions: ["日收盤價", "不含配息", "月初投入", `買賣手續費 ${input.commissionRate * 100}%`, ...(input.useMarketTaxDefaults ? selectedSymbols.map((symbol) => `${symbol} 賣出交易稅 ${symbol === "2330" ? 0.3 : 0.1}%`) : [`賣出交易稅 ${input.sellTaxRate * 100}%`]), "未計券商最低手續費", "不含滑價", "訊號使用前一交易日資料，於當日收盤執行", "報酬採時間加權", "回撤依月末收盤估算", ...selectedSymbols.flatMap((symbol) => splitAssumptions(symbol))], results,
    } };
  } catch (error) {
    const message = error instanceof Error ? error.message : "回測服務發生錯誤。";
    return { status: 502, body: { error: message } };
  }
}
