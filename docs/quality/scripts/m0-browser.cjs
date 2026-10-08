#!/usr/bin/env node
'use strict';

// Independent M0 QA. No product files are modified. All prices are synthetic.
// Requires an already built frontend + backend and installed Chrome/Playwright.
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const os = require('node:os');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');
const { chromium } = require(process.env.M0_PLAYWRIGHT_PATH || '/Users/linli/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root = path.resolve(__dirname, '../../..');
const milestone = (process.env.QA_MILESTONE || 'M0').toUpperCase();
const taskId = `${milestone}-BROWSER`;
const runtimeIdentity = process.env.QA_RUNTIME_ID || `/root/${milestone.toLowerCase()}_browser`;
const out = path.join(root, `docs/quality/evidence/${milestone.toLowerCase()}-browser`);
const dist = path.join(root, 'frontend/dist/pages/browser');
const jar = path.join(root, 'backend/target/twse-strategy-lab-api-1.0.0.jar');
const java = process.env.M0_JAVA || '/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home/bin/java';
const ports = { upstream: 19081, api: 18081, frontend: 14201 };
const base = `http://127.0.0.1:${ports.frontend}`;
const api = `http://127.0.0.1:${ports.api}`;
const checks = [], upstreamRequests = [], runtimeEvents = [];
let fixtureMode = 'normal';
const save = (name, data) => fs.writeFileSync(path.join(out, name), typeof data === 'string' ? data : JSON.stringify(data, null, 2));
const hash = file => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const ensure = (value, message) => { if (!value) throw new Error(message); };
async function check(id, description, fn, evidence = []) {
  const startedAt = new Date().toISOString();
  try { const detail = await fn(); checks.push({ id, description, status: 'PASS', startedAt, detail, evidence }); }
  catch (error) { checks.push({ id, description, status: 'FAIL', startedAt, error: error.stack, evidence }); }
  process.stdout.write(`${checks.at(-1).status} ${id}: ${description}\n`);
  save('checks.json', checks);
}
function closePrice(symbol, date) {
  const i = Math.round((date.getTime() - Date.UTC(2025, 0, 1)) / 86400000);
  return (100 + (symbol === '2330' ? 450 : 0) + Math.sin(i / 9) * 22 + Math.cos(i / 23) * 7 + i * 0.08).toFixed(2);
}
function monthRows(symbol, yyyymmdd) {
  const year = Number(yyyymmdd.slice(0, 4)), month = Number(yyyymmdd.slice(4, 6));
  const rows = [];
  for (let day = 1; day <= 31; day++) {
    const date = new Date(Date.UTC(year, month - 1, day));
    if (date.getUTCMonth() !== month - 1) break;
    if ([0, 6].includes(date.getUTCDay())) continue;
    if (month === 1 && (day === 1 || (symbol === '2330' && day === 2))) continue;
    rows.push([`${year - 1911}/${String(month).padStart(2, '0')}/${String(day).padStart(2, '0')}`, '1,000', '100,000', '100.00', '120.00', '90.00', closePrice(symbol, date), '0.00', '100']);
  }
  return rows;
}
async function listen(server, port) {
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(port, '127.0.0.1', resolve); });
}
async function checkFreePort(port) {
  const server = http.createServer(); await listen(server, port); await new Promise(resolve => server.close(resolve));
}
async function waitHealth(child) {
  for (let attempt = 0; attempt < 100; attempt++) {
    if (child.exitCode !== null) throw new Error(`Backend exited: ${child.exitCode}`);
    try { const r = await fetch(`${api}/api/health`); if (r.ok) return; } catch {}
    try { const r = await fetch(`${api}/actuator/health`); if (r.ok) return; } catch {}
    try { const r = await fetch(`${api}/api/v1/health`); if (r.ok) return; } catch {}
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error('Backend health timeout; inspect backend-runtime.log');
}
function request(symbols = ['0050']) {
  return { symbol: symbols[0], symbols, from: '2025-01-01', to: '2025-12-31', strategy: 'ma-crossover', fastWindow: 4, slowWindow: 8,
    rsiWindow: 4, rsiBuyThreshold: 30, rsiSellThreshold: 55, bollingerWindow: 4, bollingerMultiplier: 1,
    breakoutWindow: 4, drawdownBuyPercent: 10, profitSellPercent: 10, initialCapital: 100000, monthlyContribution: 10000,
    commissionRate: 0.001425, sellTaxRate: 0.001, useMarketTaxDefaults: true };
}
async function post(payload) { const response = await fetch(`${api}/api/v1/backtests`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) }); return { status: response.status, body: await response.json() }; }
async function years(page) {
  await page.getByRole('combobox', { name: '起始年度' }).selectOption('2025');
  await page.getByRole('combobox', { name: '結束年度' }).selectOption('2025');
}
async function run(page, name, expectSuccess = true) {
  const promise = page.waitForResponse(response => response.url() === `${api}/api/v1/backtests` && response.request().method() === 'POST');
  await page.getByRole('button', { name: '執行策略回測', exact: true }).click();
  await page.locator('.loading-state').waitFor({ state: 'visible' });
  ensure(await page.locator('.run-button').isDisabled(), 'Run remains enabled while loading');
  if (name === 'desktop-ma-crossover') await page.screenshot({ path: path.join(out, 'desktop-loading.png'), fullPage: true });
  const response = await promise, body = await response.json();
  save(`${name}-api.json`, { source: 'REAL_SPRING_BOOT_SYNTHETIC_UPSTREAM', status: response.status(), request: response.request().postDataJSON(), response: body });
  await page.locator('.loading-state').waitFor({ state: 'hidden' });
  if (expectSuccess) {
    ensure(response.ok(), `Expected success, received ${response.status()}: ${JSON.stringify(body)}`);
    await page.getByRole('heading', { name: '績效摘要', exact: true }).waitFor();
    ensure(await page.locator('.error-box').count() === 0, 'Error box persisted on success');
  } else {
    ensure(!response.ok(), 'Invalid upstream produced a successful partial response');
    await page.getByRole('alert').waitFor();
    ensure(await page.locator('.metric-row').count() === 0, 'Previous/partial metrics remained visible');
    ensure(typeof body.error === 'string' && typeof body.code === 'string', 'Failure omitted error/code');
  }
  return { status: response.status(), body };
}
async function overflow(page) {
  return page.evaluate(() => ({ viewport: innerWidth, body: document.body.scrollWidth, document: document.documentElement.scrollWidth,
    regions: [...document.querySelectorAll('.table-scroll')].map(element => ({ label: element.getAttribute('aria-label'), client: element.clientWidth, scroll: element.scrollWidth, overflowX: getComputedStyle(element).overflowX })),
    escaping: [...document.querySelectorAll('body *')].filter(element => { const r = element.getBoundingClientRect(); return r.width > 0 && (r.right > innerWidth + 1 || r.left < -1) && !element.closest('.table-scroll,pre'); }).slice(0, 15).map(element => ({ tag: element.tagName, class: element.className, right: element.getBoundingClientRect().right })) }));
}
async function expandEvidence(page) {
  await page.getByText('查看本次研究限制', { exact: true }).click();
  await page.getByText('計算版本與資料識別', { exact: true }).click();
  await page.getByText('成交與每日資產明細節錄', { exact: true }).click();
  await page.locator('details.provenance-details').nth(1).locator('details > summary').first().click();
}
async function main() {
  fs.mkdirSync(out, { recursive: true });
  fs.accessSync(path.join(dist, 'index.html')); fs.accessSync(jar); fs.accessSync(java);
  for (const port of Object.values(ports)) await checkFreePort(port);
  if (process.argv.includes('--prepare')) {
    save('preparation.json', { status: 'PREPARED_NOT_EXECUTED', taskId, runtimeIdentity, root, jar, dist, java,
      playwright: require.resolve(process.env.M0_PLAYWRIGHT_PATH || '/Users/linli/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'),
      portsCheckedFree: ports, node: process.version, plannedViewports: [{ width: 1440, height: 1000 }, { width: 768, height: 1024 }, { width: 390, height: 844 }], date: new Date().toISOString() });
    process.stdout.write('PREPARED. No backend/browser processes launched. Waiting for parent READY.\n'); return;
  }
  const upstream = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://127.0.0.1:${ports.upstream}`);
    const symbol = url.searchParams.get('stockNo'), date = url.searchParams.get('date');
    upstreamRequests.push({ dateTime: new Date().toISOString(), path: url.pathname, symbol, month: date, fixtureMode });
    await new Promise(resolve => setTimeout(resolve, 140));
    res.setHeader('Content-Type', 'application/json; charset=utf-8');
    if (url.pathname !== '/STOCK_DAY' || !date) { res.writeHead(404); res.end(JSON.stringify({ error: 'fixture path mismatch' })); return; }
    if (fixtureMode === 'upstream' && symbol === '2330') { res.writeHead(503); res.end(JSON.stringify({ stat: 'Synthetic unavailable' })); return; }
    if (fixtureMode === 'no-data') { res.end(JSON.stringify({ stat: 'OK', data: [] })); return; }
    const rows = monthRows(symbol, date);
    if (fixtureMode === 'invalid-row') rows[2][6] = '--';
    if (fixtureMode === 'duplicate') rows.splice(2, 0, [...rows[1]]);
    if (fixtureMode === 'non-ok') { res.end(JSON.stringify({ stat: 'Synthetic unknown market status', data: rows })); return; }
    res.end(JSON.stringify({ stat: 'OK', fields: ['日期', '成交股數', '成交金額', '開盤價', '最高價', '最低價', '收盤價', '漲跌價差', '成交筆數'], data: rows }));
  });
  const frontend = http.createServer((req, res) => {
    const url = new URL(req.url, base);
    if (url.pathname === '/config.js') { res.setHeader('Content-Type', 'application/javascript'); res.end(`window.APP_CONFIG = {apiBaseUrl: '${api}'};`); return; }
    const file = path.resolve(dist, '.' + (url.pathname === '/' ? '/index.html' : url.pathname));
    if (!file.startsWith(dist + path.sep)) { res.writeHead(403); res.end(); return; }
    const types = { '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' };
    fs.readFile(file, (error, contents) => { if (error) { res.writeHead(404); res.end(); return; } res.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream'); res.end(contents); });
  });
  const log = fs.openSync(path.join(out, 'backend-runtime.log'), 'w');
  let backend, browser;
  try {
    await listen(upstream, ports.upstream); await listen(frontend, ports.frontend);
    backend = spawn(java, ['-jar', jar, '--server.address=127.0.0.1', `--server.port=${ports.api}`, `--app.twse.base-url=http://127.0.0.1:${ports.upstream}/STOCK_DAY`, `--app.cors.allowed-origin-patterns=${base}`], { cwd: root, stdio: ['ignore', log, log] });
    await waitHealth(backend);
    browser = await chromium.launch({ channel: 'chrome', headless: true });
    const metadata = { taskId, runtimeIdentity, evidenceLabels: ['BROWSER_RUNTIME', 'AUTOMATED_TEST'], startedAt: new Date().toISOString(),
      browser: { name: 'Google Chrome', version: browser.version(), channel: 'chrome', headless: true }, platform: { os: os.platform(), release: os.release(), arch: os.arch(), node: process.version, javaVersion: spawnSync(java, ['-version'], { encoding: 'utf8' }).stderr.trim() },
      build: { jarSha256: hash(jar), indexSha256: hash(path.join(dist, 'index.html')), files: Object.fromEntries(fs.readdirSync(dist).filter(file => /\.(js|css)$/.test(file)).map(file => [file, hash(path.join(dist, file))])) },
      urls: { frontend: base, api, upstream: `http://127.0.0.1:${ports.upstream}/STOCK_DAY` }, synthetic: true,
      caveat: 'Generated weekday closes; no live TWSE requests or authoritative trading calendar. Mobile web viewport only; no native simulator/physical device/user research.', viewportRuns: [] };
    save('environment.json', metadata);
    const contexts = [];
    for (const [name, viewport] of [['desktop', { width: 1440, height: 1000 }], ['tablet', { width: 768, height: 1024 }], ['mobile-web', { width: 390, height: 844 }]]) {
      fixtureMode = 'normal';
      const context = await browser.newContext({ viewport, locale: 'zh-TW', timezoneId: 'Asia/Taipei', deviceScaleFactor: 1 }); contexts.push(context);
      await context.tracing.start({ screenshots: true, snapshots: true, sources: true });
      await context.route('**/*', route => { const host = new URL(route.request().url()).hostname; return host === '127.0.0.1' ? route.continue() : route.abort('blockedbyclient'); });
      const page = await context.newPage();
      page.on('console', message => { if (['error', 'warning'].includes(message.type())) runtimeEvents.push({ viewport: name, type: 'console', severity: message.type(), text: message.text(), location: message.location() }); });
      page.on('pageerror', error => runtimeEvents.push({ viewport: name, type: 'pageerror', text: error.stack }));
      page.on('requestfailed', req => runtimeEvents.push({ viewport: name, type: 'requestfailed', url: req.url(), error: req.failure() }));
      page.on('response', response => { if (response.status() >= 400) runtimeEvents.push({ viewport: name, type: 'http-error', status: response.status(), url: response.url(), fixtureMode }); });
      await page.goto(base); await page.getByRole('heading', { name: '設定回測', exact: true }).waitFor();
      await check(`${name}-empty`, 'Initial empty state and disclosures render', async () => { ensure(await page.locator('.empty-result').isVisible(), 'Empty state missing'); ensure(await page.locator('.comparison-preview .preview-item').count() === 5, 'Five strategy previews missing'); await page.screenshot({ path: path.join(out, `${name}-empty.png`), fullPage: true }); });
      await years(page);
      await check(`${name}-invalid-year`, 'Reversed year range disables execution', async () => { await page.getByRole('combobox', { name: '起始年度' }).selectOption('2026'); ensure(await page.locator('.run-button').isDisabled(), 'Invalid years enabled run'); ensure(await page.locator('.validation').isVisible(), 'Validation message missing'); await page.screenshot({ path: path.join(out, `${name}-invalid-years.png`), fullPage: true }); await years(page); });
      await check(`${name}-invalid-amount`, 'Invalid input disables execution without API request', async () => { await page.getByRole('spinbutton', { name: '起始投入金額', exact: true }).fill('0'); ensure(await page.locator('.run-button').isDisabled(), 'Zero capital enabled run'); await page.getByRole('spinbutton', { name: '起始投入金額', exact: true }).fill('100000'); });
      await page.getByRole('spinbutton', { name: '短均線觀察資料筆數' }).fill('4'); await page.getByRole('spinbutton', { name: '長均線觀察資料筆數' }).fill('8');
      const strategies = name === 'desktop' ? ['ma-crossover', 'rsi-reversion', 'bollinger-reversion', 'breakout', 'drawdown-entry'] : ['ma-crossover'];
      let latest;
      for (const strategy of strategies) {
        await page.getByRole('combobox', { name: '策略範本', exact: true }).selectOption(strategy);
        if (strategy === 'rsi-reversion') await page.locator('[name=rsiWindow]').fill('4');
        if (strategy === 'bollinger-reversion') { await page.locator('[name=bollingerWindow]').fill('4'); await page.locator('[name=bollingerMultiplier]').fill('1'); }
        if (strategy === 'breakout') await page.locator('[name=breakoutWindow]').fill('4');
        if (strategy === 'drawdown-entry') { await page.locator('[name=drawdownBuy]').fill('10'); await page.locator('[name=profitSell]').fill('10'); }
        await check(`${name}-${strategy}`, 'Real backend success, loading and results', async () => { latest = await run(page, `${name}-${strategy}`); ensure(latest.body.results.length === 3, 'Expected strategy, DCA and buy-and-hold'); ensure(latest.body.results[2].key.endsWith('-buy-and-hold'), 'Buy-and-hold baseline missing'); ensure(latest.body.metadata.resolvedConfig.strategy === strategy, 'Resolved strategy differs from selection'); ensure(latest.body.metadata.resolvedConfig.commissionRate === 0.001425, 'Default 0.1425 percent commission did not resolve to rate0.001425'); return { status: latest.status, resultCount: latest.body.results.length, trades: latest.body.results.map(result => result.trades.length), commissionRate: latest.body.metadata.resolvedConfig.commissionRate }; }, [`${name}-${strategy}-api.json`]);
      }
      await check(`${name}-labels-and-provenance`, 'Money/TWR, requested/actual dates, restrictions and ledger display', async () => {
        ensure(latest?.body.metadata.executionModel === 'NEXT_CLOSE_PROXY', 'Wrong execution model'); ensure(latest.body.metadata.priceAdjustmentPolicy === 'RAW_CLOSE_UNADJUSTED_V1', 'Wrong raw close policy'); ensure(latest.body.releaseStatus === 'BLOCKED', 'Release not blocked');
        for (const label of ['淨投入損益比率', '時間加權報酬 TWR', '年化 TWR', '每日 TWR 最大回撤']) ensure(await page.getByRole('columnheader', { name: label, exact: true }).isVisible(), `${label} missing`);
        ensure((await page.locator('.range-badge').innerText()).includes('2025-01-01 — 2025-12-31'), 'Requested dates missing'); ensure((await page.locator('.coverage-table tbody').innerText()).includes('2025-01-02'), 'Actual first date missing');
        await expandEvidence(page); const note = await page.locator('.research-note').innerText();
        for (const text of ['發布受阻', 'NEXT_CLOSE_PROXY', '股利', '公司行動', '交易日曆']) ensure(note.includes(text), `Disclosure missing ${text}`);
        ensure((await page.locator('.metadata-list').innerText()).includes(latest.body.metadata.datasetHash), 'Hash absent from browser');
        ensure(await page.locator('details.provenance-details').nth(1).locator('pre').first().isVisible(), 'Ledger not expanded'); await page.screenshot({ path: path.join(out, `${name}-result-details.png`), fullPage: true });
      }, [`${name}-result-details.png`]);
      await check(`${name}-overflow`, 'Horizontal overflow contained in designated scroll regions', async () => { const measurement = await overflow(page); save(`${name}-overflow.json`, measurement); ensure(measurement.body <= viewport.width + 1 && measurement.document <= viewport.width + 1, `Body horizontal overflow ${JSON.stringify(measurement)}`); ensure(measurement.regions.every(region => ['auto', 'scroll'].includes(region.overflowX)), 'Table scroll containment missing'); return measurement; });
      await check(`${name}-keyboard`, 'Tab navigation, visible focus, Enter summary and table scrolling', async () => {
        await page.locator('.brand').focus(); const focusPath = [];
        for (let i = 0; i < 35; i++) { await page.keyboard.press('Tab'); const focused = await page.evaluate(() => ({ tag: document.activeElement.tagName, name: document.activeElement.getAttribute('aria-label') || document.activeElement.getAttribute('name') || document.activeElement.textContent.trim().slice(0, 50), outline: getComputedStyle(document.activeElement).outlineStyle, outlineWidth: getComputedStyle(document.activeElement).outlineWidth })); focusPath.push(focused); if (focused.name === '執行策略回測') break; }
        ensure(focusPath.some(item => item.name === '輸入台股代碼'), 'Tab skipped symbol input'); ensure(focusPath.some(item => item.name === '執行策略回測'), 'Tab did not reach run button'); ensure(focusPath.at(-1).outline !== 'none' && focusPath.at(-1).outlineWidth !== '0px', 'Run button keyboard focus not visible');
        const detail = page.locator('details.provenance-details').first(); await detail.locator(':scope > summary').focus(); await page.keyboard.press('Enter'); ensure(await detail.getAttribute('open') === null, 'Enter did not close summary'); await page.keyboard.press('Enter'); ensure(await detail.getAttribute('open') !== null, 'Enter did not open summary');
        const region = page.getByRole('region', { name: '策略績效，可左右捲動' }); await region.focus(); const before = await region.evaluate(element => element.scrollLeft); await page.keyboard.press('ArrowRight'); await page.waitForTimeout(100); const after = await region.evaluate(element => element.scrollLeft), needsScroll = await region.evaluate(element => element.scrollWidth > element.clientWidth); ensure(!needsScroll || after > before, 'Keyboard cannot scroll table');
        save(`${name}-keyboard.json`, { focusPath, tableScroll: { before, after, needsScroll } }); return { focusedElements: focusPath.length, tableScroll: { before, after, needsScroll } };
      });
      if (name === 'desktop') {
        await check('desktop-two-symbols', 'Two symbols retain distinct observed periods and six comparison rows', async () => { await page.getByRole('button', { name: '2330 台積電', exact: true }).click(); latest = await run(page, 'desktop-two-symbols'); ensure(latest.body.assets.length === 2 && latest.body.results.length === 6, 'Two-symbol output shape wrong'); ensure(latest.body.assets[0].from !== latest.body.assets[1].from, 'Synthetic asset periods collapsed'); ensure(await page.locator('.coverage-table tbody tr').count() === 2, 'Browser lost asset period'); await page.screenshot({ path: path.join(out, 'desktop-two-symbols.png'), fullPage: true }); return latest.body.assets; });
        await check('desktop-explicit-zero-tax', 'Explicit numeric zero tax is accepted without market defaults', async () => { await page.locator('.cost-details > summary').click(); await page.locator('[name=sellTaxRate]').fill('0'); const zeroTax = await run(page, 'desktop-explicit-zero-tax'); ensure(zeroTax.body.metadata.resolvedConfig.sellTaxRate === 0 && zeroTax.body.metadata.resolvedConfig.useMarketTaxDefaults === false, 'Zero tax treated as blank/default'); ensure(Object.values(zeroTax.body.metadata.resolvedConfig.appliedSellTaxRates).every(rate => rate === 0), 'Market defaults overrode explicit zero'); await page.locator('[name=sellTaxRate]').fill(''); return zeroTax.body.metadata.resolvedConfig.appliedSellTaxRates; });
        for (const mode of ['upstream', 'no-data', 'invalid-row']) {
          fixtureMode = mode;
          await check(`desktop-error-${mode}`, 'Upstream failure shows error and no successful partial metrics', async () => { const failed = await run(page, `desktop-error-${mode}`, false); ensure((await page.getByRole('alert').innerText()).includes(failed.body.error), 'Displayed error differs from backend'); await page.screenshot({ path: path.join(out, `desktop-error-${mode}.png`), fullPage: true }); return { status: failed.status, code: failed.body.code, error: failed.body.error }; });
        }
        fixtureMode = 'normal';
      }
      metadata.viewportRuns.push({ name, viewport, mode: 'WEB_VIEWPORT', screenshot: `${name}-result-details.png`, trace: `${name}-trace.zip` });
      await context.tracing.stop({ path: path.join(out, `${name}-trace.zip`) }); await context.close();
    }
    fixtureMode = 'normal';
    await check('api-determinism', 'Same fixed synthetic data/input preserve identifiers and full response', async () => { const first = await post(request()), second = await post(request()); save('api-determinism-first.json', first); save('api-determinism-second.json', second); ensure(first.status === 200 && second.status === 200, 'Deterministic requests failed'); ensure(JSON.stringify(first) === JSON.stringify(second), 'Repeated fixed input changed response'); return { backtestId: first.body.metadata.backtestId, datasetHash: first.body.metadata.datasetHash }; });
    await check('api-accounting-and-next-observation', 'Every synthetic daily valuation and strategy execution date reconciles', async () => {
      const response = await post(request(['0050', '2330'])); ensure(response.status === 200, 'API failed'); save('api-accounting.json', response);
      let datesChecked = 0, valuationsChecked = 0, tradesChecked = 0;
      for (const result of response.body.results) {
        const symbol = result.key.includes('2330') ? '2330' : '0050';
        const dates = result.dailyEquity.map(row => row.date);
        for (const row of result.dailyEquity) { ensure(Number.isInteger(row.shares) && row.cash >= 0, 'Negative cash or fractional shares'); const expected = Number(row.cash) + row.shares * Number(closePrice(symbol, new Date(`${row.date}T00:00:00Z`))); ensure(Math.abs(expected - Number(row.equity)) < 0.011, `Daily equity mismatch ${result.key}/${row.date}: ${expected} vs ${row.equity}`); valuationsChecked++; }
        for (const trade of result.trades) { ensure(Number.isInteger(trade.quantity) && trade.cashAfter >= 0, 'Trade shares/cash invalid'); if (trade.signalDate) { ensure(dates.indexOf(trade.executionDate) === dates.indexOf(trade.signalDate) + 1, 'Strategy trade did not execute on next observed close'); datesChecked++; } tradesChecked++; }
      }
      return { valuationsChecked, tradesChecked, nextObservedExecutionsChecked: datesChecked };
    });
    await check('api-no-bars-in-requested-window', 'Observed month with no bars inside weekend request returns NO_MARKET_DATA', async () => { const result = await post({ ...request(), from: '2025-01-04', to: '2025-01-05' }); save('api-no-market-data.json', result); ensure(result.status === 404 && result.body.code === 'NO_MARKET_DATA' && !result.body.results, `Expected no-data404: ${JSON.stringify(result)}`); return result; });
    for (const [name, payload] of [['invalid-fields', { ...request(), initialCapital: 0 }], ['invalid-date', { ...request(), from: '2025-02-30' }], ['invalid-order', { ...request(), from: '2025-12-31', to: '2025-01-01' }]]) {
      await check(`api-${name}`, 'Invalid request preserves code/error without result payload', async () => { const result = await post(payload); save(`api-${name}.json`, result); ensure(result.status === 400 && result.body.code === 'INVALID_INPUT' && result.body.error && !result.body.results, `Invalid request was not rejected: ${JSON.stringify(result)}`); return result; });
    }
    for (const mode of ['duplicate', 'non-ok']) { fixtureMode = mode; await check(`api-${mode}`, 'Invalid provider dataset is rejected without partial success', async () => { const result = await post(request()); save(`api-${mode}.json`, result); ensure(result.status >= 400 && result.body.code && result.body.error && !result.body.results, `Provider error silently succeeded ${JSON.stringify(result)}`); return result; }); }
    fixtureMode = 'normal';
    await check('runtime-errors', 'No unhandled page errors or unexpected network failures', async () => { save('runtime-events.json', runtimeEvents); const unexpected = runtimeEvents.filter(event => event.type === 'pageerror' || event.type === 'requestfailed' || (event.type === 'http-error' && !['upstream', 'no-data', 'invalid-row'].includes(event.fixtureMode))); ensure(unexpected.length === 0, JSON.stringify(unexpected)); return { totalEvents: runtimeEvents.length, expectedHttpErrors: runtimeEvents.filter(event => event.type === 'http-error').length }; });
    metadata.finishedAt = new Date().toISOString(); metadata.summary = { passed: checks.filter(check => check.status === 'PASS').length, failed: checks.filter(check => check.status === 'FAIL').length };
    metadata.build.postRunJarSha256 = hash(jar);
    metadata.build.jarUnchangedDuringRun = metadata.build.jarSha256 === metadata.build.postRunJarSha256;
    metadata.runtimeOverrides = { configJs: `window.APP_CONFIG = {apiBaseUrl: '${api}'};`, configurationFileModified: false };
    save('environment.json', metadata);
    save('upstream-requests.json', upstreamRequests); save('runtime-events.json', runtimeEvents);
    process.exitCode = metadata.summary.failed ? 1 : 0;
  } finally {
    if (browser) await browser.close();
    if (backend && backend.exitCode === null) { backend.kill('SIGTERM'); await new Promise(resolve => { backend.once('exit', resolve); setTimeout(resolve, 5000); }); }
    await Promise.all([upstream, frontend].map(server => new Promise(resolve => server.close(resolve)))); fs.closeSync(log);
    save('checks.json', checks); save('upstream-requests.json', upstreamRequests); save('runtime-events.json', runtimeEvents);
  }
}
main().catch(error => { if (fs.existsSync(out)) save('harness-failure.json', { error: error.stack, at: new Date().toISOString() }); process.stderr.write(error.stack + '\n'); process.exitCode = 2; });
