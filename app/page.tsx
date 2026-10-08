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
  symbols: string[];
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
  const colors = ["#21c7a8", "#b3c2d4", "#f0a84b", "#6f8fe8", "#d780a8", "#73a86c"];
  const ticks = [min, (min + max) / 2, max];
  return <div className="chart-wrap"><svg className="chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="各策略回測資產曲線">
    {ticks.map((tick) => <g key={tick}><line x1={pad.left} x2={width - pad.right} y1={y(tick)} y2={y(tick)} className="chart-rule"/><text x={pad.left - 10} y={y(tick) + 4} textAnchor="end" className="chart-axis">${currency.format(tick)}</text></g>)}
    {results.map((result, index) => <polyline key={result.key} points={result.series.map((point, i) => `${x(i, result.series.length)},${y(point.value)}`).join(" ")} fill="none" stroke={colors[index % colors.length]} strokeWidth={index === 0 ? 3 : 2} strokeLinejoin="round" strokeLinecap="round" />)}
    {results[0]?.series.length > 1 && <><text x={pad.left} y={height - 5} className="chart-axis">{results[0].series[0].date}</text><text x={width - pad.right} y={height - 5} textAnchor="end" className="chart-axis">{results[0].series.at(-1)?.date}</text></>}
  </svg></div>;
}

export default function Home() {
  const [symbols, setSymbols] = useState(["0050"]);
  const [symbolInput, setSymbolInput] = useState("");
  const [strategy, setStrategy] = useState("ma-crossover");
  const [fromYear, setFromYear] = useState("2010");
  const [toYear, setToYear] = useState("2026");
  const [fast, setFast] = useState("20");
  const [slow, setSlow] = useState("60");
  const [rsiWindow, setRsiWindow] = useState("14");
  const [rsiBuy, setRsiBuy] = useState("30");
  const [rsiSell, setRsiSell] = useState("55");
  const [bollingerWindow, setBollingerWindow] = useState("20");
  const [bollingerMultiplier, setBollingerMultiplier] = useState("2");
  const [breakoutWindow, setBreakoutWindow] = useState("20");
  const [drawdownBuy, setDrawdownBuy] = useState("20");
  const [profitSell, setProfitSell] = useState("20");
  const [monthly, setMonthly] = useState("10000");
  const [initial, setInitial] = useState("100000");
  const [commission, setCommission] = useState("0.1425");
  const [tax, setTax] = useState("");
  const [response, setResponse] = useState<BacktestResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function runBacktest() {
    setBusy(true); setError(""); setResponse(null);
    try {
      const result = await fetch("/api/v1/backtests", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ symbol: symbols[0], symbols, strategy, from: `${fromYear}-01-01`, to: `${toYear}-12-31`, fastWindow: Number(fast), slowWindow: Number(slow), rsiWindow: Number(rsiWindow), rsiBuyThreshold: Number(rsiBuy), rsiSellThreshold: Number(rsiSell), bollingerWindow: Number(bollingerWindow), bollingerMultiplier: Number(bollingerMultiplier), breakoutWindow: Number(breakoutWindow), drawdownBuyPercent: Number(drawdownBuy), profitSellPercent: Number(profitSell), monthlyContribution: Number(monthly), initialCapital: Number(initial), commissionRate: Number(commission) / 100, sellTaxRate: Number(tax || "0.1") / 100, useMarketTaxDefaults: !tax.trim() }),
      });
      const body = await result.json();
      if (!result.ok) throw new Error(body.error || "回測暫時無法完成，請稍後再試。");
      setResponse(body as BacktestResponse);
    } catch (e) { setError(e instanceof Error ? e.message : "無法連線到回測服務。"); }
    finally { setBusy(false); }
  }

  const leading = useMemo(() => response?.results.slice().sort((a, b) => b.endingValue - a.endingValue)[0], [response]);
  const years = Array.from({ length: 17 }, (_, index) => String(2010 + index));
  const toggleSymbol = (value: string) => setSymbols((current) => current.includes(value) ? current.length > 1 ? current.filter((item) => item !== value) : current : current.length < 3 ? [...current, value] : current);
  const addSymbol = () => {
    const code = symbolInput.trim().toUpperCase();
    if (!/^\d{4,6}$/.test(code)) { setError("請輸入 4 至 6 位數字的台股代碼。"); return; }
    if (symbols.includes(code)) { setError(`${code} 已在比較清單中。`); return; }
    if (symbols.length >= 3) { setError("一次最多比較 3 檔，請先移除一檔再新增。"); return; }
    setError(""); setSymbols((current) => [...current, code]); setSymbolInput("");
  };
  const strategyOptions = [
    ["ma-crossover", "雙均線交叉", "短均線高於長均線持有；反向時退場"],
    ["rsi-reversion", "RSI 均值回歸", "RSI 低於買進門檻進場，高於賣出門檻出場"],
    ["bollinger-reversion", "布林通道回歸", "收盤跌破下軌買進，回到中線賣出"],
    ["breakout", "區間突破", "突破前 N 日高點買進，跌回均線下方賣出"],
    ["drawdown-entry", "自訂回跌 / 獲利", "距近一年高點回跌達門檻買進，達目標報酬賣出"],
  ];

  return <main id="backtest-workspace" className="app-shell">
    <header className="topbar"><a href="#" className="brand"><span className="brand-mark"><i /><i /><i /></span><span>策略<span className="brand-light">實驗室</span></span></a><div className="topbar-right"><span className="market-pill"><span className="pulse-dot" />台股歷史資料</span><span className="header-divider"/><span className="header-caption">研究工具 · 01</span></div></header>
    <div className="content">
      <div className="page-intro"><div><p className="eyebrow">STRATEGY BACKTESTING WORKSPACE</p><h1>讓策略接受歷史考驗。</h1><p className="intro-copy">用同一段行情、相同投入金額，檢視你的規則和定期定額走出什麼結果。</p></div><div className="intro-date"><span className="live-dot"/>資料來源：臺灣證券交易所</div></div>
      <section className="workspace">
        <aside className="config-panel">
          <div className="panel-heading"><div><span className="step">01</span><h2>設定回測</h2></div><span className="tiny-label">PARAMETERS</span></div>
          <div className="field"><label>回測標的（最多 3 檔）</label><div className="symbol-add"><input aria-label="輸入台股代碼" inputMode="numeric" placeholder="輸入代碼，例如 2603" value={symbolInput} onChange={(e) => setSymbolInput(e.target.value.replace(/\D/g, "").slice(0, 6))} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addSymbol(); } }} /><button type="button" onClick={addSymbol} disabled={symbols.length >= 3}>加入</button></div><div className="selected-symbols" aria-label="已選標的">{symbols.map((code) => <span className="selected-symbol" key={code}>{code}<button type="button" aria-label={`移除 ${code}`} onClick={() => toggleSymbol(code)} disabled={symbols.length <= 1}>×</button></span>)}</div><div className="symbol-presets"><span>快速加入</span>{[["0050", "台灣50"], ["0056", "高股息"], ["2330", "台積電"]].map(([code, name]) => <button type="button" key={code} onClick={() => { if (!symbols.includes(code) && symbols.length < 3) setSymbols((current) => [...current, code]); }} disabled={symbols.includes(code) || symbols.length >= 3}>{code} {name}</button>)}</div><span className="field-hint">輸入 TWSE 上市股票代碼即可回測；快速選項僅供方便，不限於這三檔。</span></div>
          <div className="field"><label>回測期間</label><div className="range-inputs"><select aria-label="起始年度" value={fromYear} onChange={(e) => setFromYear(e.target.value)}>{years.map((year) => <option key={year}>{year}</option>)}</select><span>至</span><select aria-label="結束年度" value={toYear} onChange={(e) => setToYear(e.target.value)}>{years.map((year) => <option key={year}>{year}</option>)}</select></div><span className="field-hint">證交所此行情端點提供 2010 年起資料</span></div>
          <div className="field"><label htmlFor="strategy">策略範本</label><select id="strategy" value={strategy} onChange={(e) => setStrategy(e.target.value)}>{strategyOptions.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select><div className="strategy-chip"><span className="strategy-icon">⌁</span><span><strong>{strategyOptions.find(([id]) => id === strategy)?.[1]}</strong><small>{strategyOptions.find(([id]) => id === strategy)?.[2]}</small></span><span className="custom-tag">可調參數</span></div>
            {strategy === "ma-crossover" && <div className="range-inputs window-inputs"><label>短均線<input type="number" min="2" max="250" value={fast} onChange={(e) => setFast(e.target.value)} aria-label="短均線交易日"/><span>日</span></label><label>長均線<input type="number" min="3" max="500" value={slow} onChange={(e) => setSlow(e.target.value)} aria-label="長均線交易日"/><span>日</span></label></div>}
            {strategy === "rsi-reversion" && <div className="range-inputs strategy-inputs"><label>RSI 週期<input type="number" min="2" max="100" value={rsiWindow} onChange={(e) => setRsiWindow(e.target.value)}/></label><label>買進低於<input type="number" min="1" max="49" value={rsiBuy} onChange={(e) => setRsiBuy(e.target.value)}/></label><label>賣出高於<input type="number" min="51" max="99" value={rsiSell} onChange={(e) => setRsiSell(e.target.value)}/></label></div>}
            {strategy === "bollinger-reversion" && <div className="range-inputs strategy-inputs"><label>計算週期<input type="number" min="2" max="200" value={bollingerWindow} onChange={(e) => setBollingerWindow(e.target.value)}/></label><label>標準差倍數<input type="number" min="0.5" max="5" step="0.1" value={bollingerMultiplier} onChange={(e) => setBollingerMultiplier(e.target.value)}/></label></div>}
            {strategy === "breakout" && <div className="range-inputs strategy-inputs"><label>突破回看日數<input type="number" min="2" max="250" value={breakoutWindow} onChange={(e) => setBreakoutWindow(e.target.value)}/></label></div>}
            {strategy === "drawdown-entry" && <><div className="range-inputs strategy-inputs"><label>回跌近一年高點<input type="number" min="1" max="80" value={drawdownBuy} onChange={(e) => setDrawdownBuy(e.target.value)}/><small>% 才買進</small></label><label>持倉報酬達<input type="number" min="1" max="200" value={profitSell} onChange={(e) => setProfitSell(e.target.value)}/><small>% 才賣出</small></label></div><span className="field-hint">訊號以昨日收盤判定，今天收盤模擬成交。</span></>}</div>
          <div className="field"><label>投入金額</label><div className="range-inputs money-inputs"><label>起始投入<div className="money-field"><span>$</span><input inputMode="numeric" value={initial} onChange={(e) => setInitial(e.target.value)} aria-label="起始投入金額"/></div></label><label>每月投入<div className="money-field"><span>$</span><input inputMode="numeric" value={monthly} onChange={(e) => setMonthly(e.target.value)} aria-label="每月投入金額"/></div></label></div></div>
          <details className="cost-details"><summary>交易成本假設 <span>佣金與證交稅</span></summary><div className="range-inputs cost-fields"><label>單邊手續費 %<input type="number" step="0.01" value={commission} onChange={(e) => setCommission(e.target.value)}/><small>預設為費率上限</small></label><label>賣出交易稅 %<input type="number" step="0.01" value={tax} placeholder="依標的預設" onChange={(e) => setTax(e.target.value)}/><small>留白時，各標的套用預設稅率</small></label></div></details>
          <button className="run-button" onClick={runBacktest} disabled={busy || symbols.length === 0 || Number(fromYear) > Number(toYear) || (strategy === "ma-crossover" && Number(fast) >= Number(slow))}>{busy ? <><span className="spinner"/>讀取行情並計算中…</> : <>執行策略回測</>}</button>
          {(Number(fromYear) > Number(toYear) || (strategy === "ma-crossover" && Number(fast) >= Number(slow))) && <p className="validation">請確認年度順序，且短均線小於長均線。</p>}
          {error && <p className="error-box" role="alert">{error}</p>}
          <p className="config-footnote">回測以日收盤價判斷，均線訊號採前一交易日資料，避免使用未來資訊。</p>
        </aside>

        <section className="results-panel" aria-live="polite">
          <div className="results-top"><div className="panel-heading results-heading"><div><span className="step">02</span><h2>策略比較</h2></div><span className="compare-note">相同投入金額 · 費用納入</span></div>
            {response && <div className="range-badge">{response.symbols.join(" / ")} · {response.from} — {response.to}</div>}
          </div>
          {busy ? <div className="loading-state"><span className="spinner dark"/><strong>正在整理證交所歷史行情</strong><span>首次載入較長期間可能需要一點時間。</span></div> : response ? <>
            <div className="metric-row"><div className="metric-card featured"><span className="metric-label">資產最高策略</span><strong>${currency.format(leading?.endingValue ?? 0)}</strong><span className="metric-sub">{leading?.name} · 報酬 {percent(leading?.totalReturn ?? 0)}</span></div><div className="metric-card"><span className="metric-label">每組總投入</span><strong>${currency.format(response.results[0]?.contributed ?? 0)}</strong><span className="metric-sub">含初始投入與每月加碼</span></div><div className="metric-card"><span className="metric-label">比較標的 / 交易日</span><strong>{response.symbols.length} 檔 · {currency.format(response.tradingDays)}</strong><span className="metric-sub">行情來源：TWSE</span></div></div>
            <div className="chart-card"><div className="chart-title-row"><div><h3>資產成長曲線</h3><span>新台幣 · 含未投資現金 · 不含配息</span></div><div className="legend">{response.results.map((result, i) => <span key={result.key}><i className={`legend-dot legend-${i}`}/>{result.name}</span>)}</div></div><LineChart results={response.results}/></div>
            <div className="comparison-table"><div className="table-heading"><h3>績效摘要</h3><span>歷史模擬，不代表未來表現</span></div><div className="table-scroll"><table><thead><tr><th>策略</th><th>期末資產</th><th>總報酬</th><th>年化報酬</th><th>月末最大回撤</th></tr></thead><tbody>{response.results.map((item, i) => <tr key={item.key}><td><i className={`legend-dot legend-${i}`}/>{item.name}</td><td className="table-value">${currency.format(item.endingValue)}</td><td className={item.totalReturn >= 0 ? "positive" : "negative"}>{percent(item.totalReturn)}</td><td>{percent(item.annualizedReturn)}</td><td className="negative">−{item.maxDrawdown.toFixed(1)}%</td></tr>)}</tbody></table></div></div>
            <div className="assumption-note"><strong>回測假設</strong><span>{response.assumptions.join(" · ")} · 報酬率為時間加權；期末資產含投入本金。</span></div>
          </> : <>
            <div className="empty-result"><div className="empty-chart"><div className="empty-y"><span>資產價值</span><i>$</i><i>$</i><i>$</i></div><svg viewBox="0 0 620 190" aria-hidden="true"><path d="M0 155H620M0 108H620M0 60H620M0 14H620" className="empty-rule"/><path d="M0 160 C36 146,46 136,78 142 S127 153,153 127 S184 120,209 133 S257 111,279 117 S313 100,337 106 S366 84,393 99 S421 76,449 85 S473 61,499 75 S532 47,555 59 S585 24,620 18" className="ghost-line"/></svg><div className="empty-x"><span>開始日</span><span>回測期間</span><span>結束日</span></div></div><div className="empty-message"><span className="empty-spark">✳</span><div><strong>你的策略，會走出自己的曲線。</strong><p>設定標的與期間，開始比較均線策略和定期定額。</p></div></div></div>
            <div className="comparison-preview"><div className="preview-head"><h3>策略範本</h3><span>所有結果都與定期定額比較</span></div>{strategyOptions.slice(0, 4).map(([id, name], index) => <div className="preview-item" key={id}><i className={`legend-dot legend-${index}`}/><span>{name}</span><small>{id === "ma-crossover" ? "趨勢" : id === "rsi-reversion" || id === "bollinger-reversion" ? "均值回歸" : "突破"}</small></div>)}</div>
          </>}
        </section>
      </section>
      <footer className="disclaimer"><span className="disclaimer-icon">i</span><p><strong>研究用途，非投資建議。</strong>回測依歷史收盤價計算，不含配息、滑價與券商最低手續費；已知分割依公告調整。報酬數字是歷史模擬，不保證未來績效。實際交易成本依券商與標的而異。</p><a href="https://openapi.twse.com.tw/" target="_blank" rel="noreferrer">資料來源說明</a></footer>
    </div>
  </main>;
}
