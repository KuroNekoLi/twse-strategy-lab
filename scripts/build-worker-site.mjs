import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { mkdirSync, readFileSync, unlinkSync, writeFileSync } from "node:fs";
import path from "node:path";

const root = process.cwd();
const esbuild = path.join(root, "node_modules/.bin/esbuild");
const workDir = path.join(root, ".sites-runtime");
const serverDir = path.join(root, "dist/server");
mkdirSync(workDir, { recursive: true });
mkdirSync(serverDir, { recursive: true });

const clientBundle = path.join(workDir, "strategy-lab-app.js");
execFileSync(esbuild, [
  path.join(root, "app/static-entry.tsx"), "--bundle", "--format=esm", "--platform=browser",
  "--target=es2022", "--jsx=automatic", "--minify", `--outfile=${clientBundle}`,
], { cwd: root, stdio: "inherit" });

const rawCss = readFileSync(path.join(root, "app/globals.css"), "utf8");
const appCss = rawCss.split(/\r?\n/).filter((line) => !line.startsWith("@import ") && !line.startsWith("@theme inline")).join("\n");
const appJavaScript = readFileSync(clientBundle, "utf8");
const assetVersion = createHash("sha256").update(appJavaScript).update(appCss).digest("hex").slice(0, 12);
const icon = encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="13" fill="#142638"/><path d="M12 34V25M21 34V17M30 34V12M39 34V20" stroke="#21C7A8" stroke-width="4" stroke-linecap="round"/><path d="m10 20 10-7 9-3 9 4" stroke="#DDF7EF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>');
const siteUrl = "https://twse-strategy-lab.alice-margatroid-lov.chatgpt.site/";
const siteTitle = "台股策略回測工具｜均線、RSI 與定期定額比較";
const siteDescription = "用臺灣證券交易所上市股票歷史日行情，回測雙均線、RSI、布林通道、區間突破與自訂規則，並和定期定額比較。";
const structuredData = JSON.stringify({
  "@context": "https://schema.org",
  "@type": "WebApplication",
  name: "台股策略回測實驗室",
  url: siteUrl,
  description: siteDescription,
  applicationCategory: "FinanceApplication",
  operatingSystem: "Web",
  inLanguage: "zh-TW",
  featureList: ["台股歷史策略回測", "雙均線、RSI、布林通道與區間突破策略", "自訂回跌買進與獲利賣出條件", "與定期定額比較"],
});
const pageHtml = `<!doctype html><html lang="zh-TW"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#142638"><meta name="description" content="${siteDescription}"><meta name="robots" content="index, follow, max-image-preview:large"><link rel="canonical" href="${siteUrl}"><meta property="og:type" content="website"><meta property="og:locale" content="zh_TW"><meta property="og:site_name" content="策略實驗室"><meta property="og:url" content="${siteUrl}"><meta property="og:title" content="${siteTitle}"><meta property="og:description" content="${siteDescription}"><meta name="twitter:card" content="summary"><meta name="twitter:title" content="台股策略回測工具｜策略實驗室"><meta name="twitter:description" content="使用台股歷史日行情，自訂標的、期間與策略，查看回測結果。"><title>${siteTitle}</title><script type="application/ld+json">${structuredData}</script><link rel="icon" type="image/svg+xml" href="data:image/svg+xml,${icon}"><link rel="stylesheet" href="/style.css?v=${assetVersion}"></head><body><div id="root"></div><section class="seo-guide" aria-labelledby="seo-guide-title"><div class="seo-guide-inner"><p class="seo-kicker">台股研究與策略回測</p><h2 id="seo-guide-title">用歷史行情比較台股投資策略</h2><p class="seo-lede">策略實驗室讓你自行選擇台股標的與回測期間，檢視不同買賣規則的歷史結果，並在相同投入條件下和定期定額比較。</p><a class="seo-workspace-link" href="#backtest-workspace">前往回測工作台</a><div class="seo-guide-grid"><section><h3>可比較的策略</h3><ul><li>雙均線交叉：依短期與長期均線的交叉訊號調整持倉。</li><li>RSI 均值回歸：自訂 RSI 買進與賣出門檻。</li><li>布林通道與區間突破：依價格相對通道或近期高點產生訊號。</li><li>自訂回跌／獲利：設定從近一年高點回跌多少買進，以及持倉報酬多少賣出。</li></ul></section><section><h3>設定標的與投入條件</h3><p>輸入 TWSE 上市股票代碼，選擇回測年度、起始投入和每月投入金額。可調整手續費與賣出交易稅，讓策略和定期定額使用一致的比較條件。</p></section><section><h3>查看回測結果</h3><p>結果包含資產成長曲線、期末資產、總報酬、年化報酬和最大回撤。這些指標用來理解歷史表現與波動，不能單獨代表未來績效。</p></section><section><h3>資料來源與回測限制</h3><p>行情取自臺灣證券交易所上市股票歷史日資料，介面目前提供 2010 年起的回測期間。策略訊號使用前一交易日完成的收盤資料，並以當日收盤價模擬成交；目前不計配息、滑價與券商最低手續費。</p></section></div><p class="seo-disclaimer"><strong>研究用途，非投資建議。</strong> 回測是依歷史資料和明示假設進行的模擬，不保證未來結果。</p></div></section><script type="module" src="/app.js?v=${assetVersion}"></script></body></html>`;
const generatedAssets = path.join(serverDir, "site-assets.ts");
writeFileSync(generatedAssets, `export const pageHtml = ${JSON.stringify(pageHtml)};\nexport const appJavaScript = ${JSON.stringify(appJavaScript)};\nexport const appCss = ${JSON.stringify(appCss)};\n`);

const workerOutput = path.join(serverDir, "index.js");
execFileSync(esbuild, [
  path.join(root, "lib/worker-entry.ts"), "--bundle", "--format=esm", "--platform=browser",
  "--target=es2022", "--minify", `--outfile=${workerOutput}`,
], { cwd: root, stdio: "inherit" });
unlinkSync(generatedAssets);
console.log(JSON.stringify({ output: workerOutput, bytes: readFileSync(workerOutput).byteLength }));
