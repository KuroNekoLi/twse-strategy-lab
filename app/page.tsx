"use client";

import { useMemo, useState } from "react";

type Point = { date: string; value: number };
type StrategyResult = {
  key: string;
  name: string;
  endingValue: number;
  contributed: number;
  totalReturn: number;
  annualizedReturn: number;
  maxDrawdown: number;
  series: Point[];
};
type BacktestResponse = {
  symbol: string;
  from: string;
  to: string;
  tradingDays: number;
  dataSource: string;
  assumptions: string[];
  results: StrategyResult[];
};

const currency = new Intl.NumberFormat("zh-TW", { maximumFractionDigits: 0 });
const percent = (value: number) => `${value >= 0 ? "+" : "−"}${Math.abs(value).toFixed(1)}%`;

function LineChart({ results }: { results: StrategyResult[] }) {
  const all = results.flatMap((result) => result.series);
  if (!all.length) return <div className="chart-empty"><span className="chart-grid" /><span className="chart-empty-mark">↗</span><p>執行回測後，資產曲線會顯示在這裡</p></div>;
  const width = 860, height = 290, pad = { top: 16, right: 14, bottom: 32, left: 60 };
  const values = all.map((point) => point.value);
  const min = Math.min(...values) * 0.94;
  const max = Math.max(...values) * 1.04;
  const x = (index: number, count: number) => pad.left + (index / Math.max(count - 1, 1)) * (width - pad.left - pad.right);
  const y = (value: number) => height - pad.bottom - ((value - min) / Math.max(max - min, 1)) * (height - pad.top - pad.bottom);
  const colors = ["#21c7a8", "#b3c2d4", "#f0a84b"];
  const ticks = [min, (min + max) / 2, max];
  return <div className="chart-wrap"><svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="各策略回測資產曲線">
    {ticks.map((tick) => <g key={tick}><line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} className="chart-rule"/><text x={pad.left - 10} y={y(tick) + 4} textAnchor="end" className="chart-axis">${currency.format(tick)}</text></g>)}
    {results.map((result, index) => <polyline key={result.key} points={result.series.map((point, i) => `${x(i, result.series.length)},${y(point.value)}`).join(" ")} fill="none" stroke={colors[index % colors.length]} strokeWidth={index === 0 ? 3 : 2} strokeLinejoin="round" strokeLinecap="round" />)}
    {results[0]?.series.length > 1 && <><text x={pad.left} y={height - 5} className="chart-axis">{results[0].series[0].date}</text><text x={width - pad.right} y={height - 5} textAnchor="end" className="chart-axis">{results[0].series.at(-1)?.date}</text></>}
  </svg></div>;
}

export default function Home() {
  const [symbol, setSymbol] = useState("0050");
  const [fromYear, setFromYear] = useState("2010");
  const [toYear, setToYear] = useState("2026");
  const [fast, setFast] = useState("20");
  const [slow, setSlow] = useState("60");
  const [monthly, setMonthly] = useState("10000");
  const [initial, setInitial] = useState("100000");
  const [commission, setCommission] = useState("0.1425");
  const [tax, setTax] = useState("0.1");
  const [response, setResponse] = useState<BacktestResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function runBacktest() {
    setBusy(true); setError(""); setResponse(null);
    try {
      const result = await fetch("/api/v1/backtests", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ symbol, from: `${fromYear}-01-01`, to: `${toYear}-12-31`, fastWindow: Number(fast), slowWindow: Number(slow), monthlyContribution: Number(monthly), initialCapital: Number(initial), commissionRate: Number(commission) / 100, sellTaxRate: Number(tax) / 100 }),
      });
      const body = await result.json();
      if (!result.ok) throw new Error(body.error || "回測暫時無法完成，請稍後再試。");
      setResponse(body as BacktestResponse);
    } catch (e) { setError(e instanceof Error ? e.message : "無法連線到回測服務。"); }
    finally { setBusy(false); }
  }

  const leading = useMemo(() => response?.results.slice().sort((a, b) => b.endingValue - a.endingValue)[0], [response]);
  const years = Array.from({ length: 17 }, (_, index) => String(2010 + index));

  return <main className="app-shell">
    <header className="topbar"><a href="#" className="brand"><span className="brand-mark"><i /><i /><i /></span><span>策略<span className="brand-light">實驗室</span></span></a><div className="topbar-right"><span className="market-pill"><span className="pulse-dot" />台股歷史資料</span><span className="header-divider"/><span className="header-caption">研究工具 · 01</span></div></header>
    <div className="content">
      <div className="page-intro"><div><p className="eyebrow">STRATEGY BACKTESTING WORKSPACE</p><h1>讓策略接受歷史考驗。</h1><p className="intro-copy">用同一段行情、相同投入金額，檢視你的規則和定期定額走出什麼結果。</p></div><div className="intro-date"><span className="live-dot"/>資料來源：臺灣證券交易所</div></div>
      <section className="workspace">
        <aside className="config-panel">
          <div className="panel-heading"><div><span className="step">01</span><h2>設定回測</h2></div><span className="tiny-label">PARAMETERS</span></div>
          <div className="field"><label htmlFor="symbol">投資標的</label><select id="symbol" value={symbol} onChange={(e) => { setSymbol(e.target.value); setTax(e.target.value === "2330" ? "0.3" : "0.1"); }}><option value="0050">0050 · 元大台灣50</option><option value="0056">0056 · 元大高股息</option><option value="2330">2330 · 台積電</option></select><span className="field-hint">目前支援證交所上市標的</span></div>
          <div className="field"><label>回測期間</label><div className="range-inputs"><select aria-label="起始年度" value={fromYear} onChange={(e) => setFromYear(e.target.value)}>{years.map((year) => <option key={year}>{year}</option>)}</select><span>至</span><select aria-label="結束年度" value={toYear} onChange={(e) => setToYear(e.target.value)}>{years.map((year) => <option key={year}>{year}</option>)}</select></div><span className="field-hint">證交所此行情端點提供 2010 年起資料</span></div>
          <div className="field"><label>策略規則</label><div className="strategy-chip"><span className="strategy-icon">⌁</span><span><strong>雙均線趨勢</strong><small>短均線上穿長均線時持有</small></span><span className="custom-tag">自訂</span></div><div className="range-inputs window-inputs"><label>短均線<input type="number" min="2" max="250" value={fast} onChange={(e) => setFast(e.target.value)} aria-label="短均線交易日"/><span>日</span></label><label>長均線<input type="number" min="3" max="500" value={slow} onChange={(e) => setSlow(e.target.value)} aria-label="長均線交易日"/><span>日</span></label></div></div>
          <div className="field"><label>投入金額</label><div className="range-inputs money-inputs"><label>起始投入<div className="money-field"><span>$</span><input inputMode="numeric" value={initial} onChange={(e) => setInitial(e.target.value)} aria-label="起始投入金額"/></div></label><label>每月投入<div className="money-field"><span>$</span><input inputMode="numeric" value={monthly} onChange={(e) => setMonthly(e.target.value)} aria-label="每月投入金額"/></div></label></div></div>
          <details className="cost-details"><summary>交易成本假設 <span>佣金與證交稅</span></summary><div className="range-inputs cost-fields"><label>單邊手續費 %<input type="number" step="0.01" value={commission} onChange={(e) => setCommission(e.target.value)}/><small>預設為費率上限</small></label><label>賣出交易稅 %<input type="number" step="0.01" value={tax} onChange={(e) => setTax(e.target.value)}/><small>依 ETF 或個股設定</small></label></div></details>
          <button className="run-button" onClick={runBacktest} disabled={busy || Number(fromYear) > Number(toYear) || Number(fast) >= Number(slow)}>{busy ? <><span className="spinner"/>讀取行情並計算中…</> : <>執行策略回測</>}</button>
          {(Number(fromYear) > Number(toYear) || Number(fast) >= Number(slow)) && <p className="validation">請確認年度順序，且短均線小於長均線。</p>}
          {error && <p className="error-box" role="alert">{error}</p>}
          <p className="config-footnote">回測以日收盤價判斷，均線訊號採前一交易日資料，避免使用未來資訊。</p>
        </aside>

        <section className="results-panel" aria-live="polite">
          <div className="results-top"><div className="panel-heading results-heading"><div><span className="step">02</span><h2>策略比較</h2></div><span className="compare-note">相同投入金額 · 費用納入</span></div>
            {response && <div className="range-badge">{response.from} — {response.to}</div>}
          </div>
          {busy ? <div className="loading-state"><span className="spinner dark"/><strong>正在整理證交所歷史行情</strong><span>首次載入較長期間可能需要一點時間。</span></div> : response ? <>
            <div className="metric-row"><div className="metric-card featured"><span className="metric-label">資產最高策略</span><strong>${currency.format(leading?.endingValue ?? 0)}</strong><span className="metric-sub">{leading?.name} · 報酬 {percent(leading?.totalReturn ?? 0)}</span></div><div className="metric-card"><span className="metric-label">總投入金額</span><strong>${currency.format(response.results[0]?.contributed ?? 0)}</strong><span className="metric-sub">含初始投入與每月加碼</span></div><div className="metric-card"><span className="metric-label">有效交易日</span><strong>{currency.format(response.tradingDays)}</strong><span className="metric-sub">行情來源：TWSE</span></div></div>
            <div className="chart-card"><div className="chart-title-row"><div><h3>資產成長曲線</h3><span>新台幣 · 含未投資現金 · 不含配息</span></div><div className="legend">{response.results.map((result, i) => <span key={result.key}><i className={`legend-dot legend-${i}`}/>{result.name}</span>)}</div></div><LineChart results={response.results}/></div>
            <div className="comparison-table"><div className="table-heading"><h3>績效摘要</h3><span>歷史模擬，不代表未來表現</span></div><div className="table-scroll"><table><thead><tr><th>策略</th><th>期末資產</th><th>總報酬</th><th>年化報酬</th><th>月末最大回撤</th></tr></thead><tbody>{response.results.map((item, i) => <tr key={item.key}><td><i className={`legend-dot legend-${i}`}/>{item.name}</td><td className="table-value">${currency.format(item.endingValue)}</td><td className={item.totalReturn >= 0 ? "positive" : "negative"}>{percent(item.totalReturn)}</td><td>{percent(item.annualizedReturn)}</td><td className="negative">−{item.maxDrawdown.toFixed(1)}%</td></tr>)}</tbody></table></div></div>
            <div className="assumption-note"><strong>回測假設</strong><span>{response.assumptions.join(" · ")} · 報酬率為時間加權；期末資產含投入本金。</span></div>
          </> : <>
            <div className="empty-result"><div className="empty-chart"><div className="empty-y"><span>資產價值</span><i>$</i><i>$</i><i>$</i></div><svg viewBox="0 0 620 190" aria-hidden="true"><path d="M0 155H620M0 108H620M0 60H620M0 14H620" className="empty-rule"/><path d="M0 160 C36 146,46 136,78 142 S127 153,153 127 S184 120,209 133 S257 111,279 117 S313 100,337 106 S366 84,393 99 S421 76,449 85 S473 61,499 75 S532 47,555 59 S585 24,620 18" className="ghost-line"/></svg><div className="empty-x"><span>開始日</span><span>回測期間</span><span>結束日</span></div></div><div className="empty-message"><span className="empty-spark">✳</span><div><strong>你的策略，會走出自己的曲線。</strong><p>設定標的與期間，開始比較均線策略和定期定額。</p></div></div></div>
            <div className="comparison-preview"><div className="preview-head"><h3>本次比較</h3><span>同額投入</span></div><div className="preview-item"><i className="legend-dot legend-0"/><span>雙均線趨勢</span><small>20 日 / 60 日</small></div><div className="preview-item"><i className="legend-dot legend-1"/><span>定期定額</span><small>每月固定投入</small></div></div>
          </>}
        </section>
      </section>
      <footer className="disclaimer"><span className="disclaimer-icon">i</span><p><strong>研究用途，非投資建議。</strong>回測依歷史收盤價計算，不含配息、滑價與券商最低手續費；已知分割依公告調整。報酬數字是歷史模擬，不保證未來績效。實際交易成本依券商與標的而異。</p><a href="https://openapi.twse.com.tw/" target="_blank" rel="noreferrer">資料來源說明</a></footer>
    </div>
  </main>;
}
