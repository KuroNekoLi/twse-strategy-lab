import { execFileSync } from "node:child_process";
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
const icon = encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="13" fill="#142638"/><path d="M12 34V25M21 34V17M30 34V12M39 34V20" stroke="#21C7A8" stroke-width="4" stroke-linecap="round"/><path d="m10 20 10-7 9-3 9 4" stroke="#DDF7EF" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>');
const pageHtml = `<!doctype html><html lang="zh-TW"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#142638"><meta name="description" content="使用臺灣證券交易所歷史日行情，比較自訂均線策略與定期定額的回測結果。"><title>台股策略回測實驗室｜策略實驗室</title><link rel="icon" type="image/svg+xml" href="data:image/svg+xml,${icon}"><link rel="stylesheet" href="/style.css"></head><body><div id="root"></div><script type="module" src="/app.js"></script></body></html>`;
const generatedAssets = path.join(serverDir, "site-assets.ts");
writeFileSync(generatedAssets, `export const pageHtml = ${JSON.stringify(pageHtml)};\nexport const appJavaScript = ${JSON.stringify(appJavaScript)};\nexport const appCss = ${JSON.stringify(appCss)};\n`);

const workerOutput = path.join(serverDir, "index.js");
execFileSync(esbuild, [
  path.join(root, "lib/worker-entry.ts"), "--bundle", "--format=esm", "--platform=browser",
  "--target=es2022", "--minify", `--outfile=${workerOutput}`,
], { cwd: root, stdio: "inherit" });
unlinkSync(generatedAssets);
console.log(JSON.stringify({ output: workerOutput, bytes: readFileSync(workerOutput).byteLength }));
