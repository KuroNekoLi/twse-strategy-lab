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
  const request = new Request(url.toString(), { headers: { Accept: "application/json" } });
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
  let current = request;
  for (let hop = 0; hop < 4; hop++) {
    const response = await fetch(current, { redirect: "manual", signal: AbortSignal.timeout(15000) });
    if (![301, 302, 303, 307, 308].includes(response.status)) return response;
    const location = response.headers.get("location");
    if (!location) return response;
    const nextUrl = new URL(location, current.url);
    if (nextUrl.protocol !== "https:" || !(nextUrl.hostname === "twse.com.tw" || nextUrl.hostname.endsWith(".twse.com.tw"))) {
      throw new Error("證交所行情服務導向了非證交所網址，已停止請求。");
    }
    current = new Request(nextUrl, { headers: { Accept: "application/json" } });
  }
  throw new Error("證交所行情服務轉址次數過多，請稍後再試。");
}

async function loadBars(symbol: string, months: string[]) {
  const rows: DailyBar[] = [];
  for (let i = 0; i < months.length; i += 8) {
    const group = await Promise.all(months.slice(i, i + 8).map((month) => loadMonth(symbol, month)));
    rows.push(...group.flat());
  }
  return rows.sort((a, b) => a.date.localeCompare(b.date));
}

export async function handleBacktest(input: BacktestInput) {
  try {
    if (!symbols.has(input.symbol)) return { status: 400, body: { error: "目前示範標的為 0050、0056 與 2330。" } };
    const today = new Date().toISOString().slice(0, 10);
    if (!/^2010-\d\d-\d\d$/.test(input.from) || !/^\d{4}-\d\d-\d\d$/.test(input.to) || input.from > input.to || input.from > today) return { status: 400, body: { error: "回測日期格式不正確；此資料服務目前提供 2010 年起的行情。" } };
    const effectiveTo = input.to > today ? today : input.to;
    const months = monthsBetween(input.from.slice(0, 7), effectiveTo.slice(0, 7));
    if (months.length > 204) return { status: 400, body: { error: "單次回測最多 17 年，請縮短期間。" } };
    if (!Number.isInteger(input.fastWindow) || !Number.isInteger(input.slowWindow) || input.fastWindow < 2 || input.fastWindow >= input.slowWindow || input.slowWindow > 500) return { status: 400, body: { error: "請確認均線日數，短均線需小於長均線。" } };
    if (![input.monthlyContribution, input.initialCapital, input.commissionRate, input.sellTaxRate].every((value) => Number.isFinite(value) && value >= 0) || input.initialCapital <= 0) return { status: 400, body: { error: "投入金額和交易成本需為有效的非負數值。" } };

    const rawBars = (await loadBars(input.symbol, months)).filter((bar) => bar.date >= input.from && bar.date <= effectiveTo);
    if (!rawBars.length) return { status: 404, body: { error: "這段期間沒有找到可用的證交所日行情。" } };
    const bars = adjustKnownSplits(input.symbol, rawBars);
    const results = runBacktest(input, bars);
    return { status: 200, body: {
      symbol: input.symbol, from: bars[0].date, to: bars.at(-1)!.date, tradingDays: bars.length,
      dataSource: "臺灣證券交易所 STOCK_DAY", assumptions: ["日收盤價", "不含配息", "月初投入", `買賣手續費 ${input.commissionRate * 100}%`, `賣出交易稅 ${input.sellTaxRate * 100}%`, "未計券商最低手續費", "不含滑價", "報酬採時間加權", "回撤依月末收盤估算", ...splitAssumptions(input.symbol)], results,
    } };
  } catch (error) {
    const message = error instanceof Error ? error.message : "回測服務發生錯誤。";
    return { status: 502, body: { error: message } };
  }
}
