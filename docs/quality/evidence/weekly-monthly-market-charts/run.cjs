const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('/Users/linli/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');

const root = '/Users/linli/dev_spring-boot/twse-strategy-lab';
const out = path.join(root, 'docs/quality/evidence/weekly-monthly-market-charts');
const dist = path.join(root, 'frontend/dist/pages/browser');
const webPort = 18392;
const apiBase = 'http://127.0.0.1:14392';
const webBase = `http://127.0.0.1:${webPort}`;
const checks = [];
const apiCalls = [];
const pageErrors = [];
const consoleErrors = [];

function assert(condition, message) { if (!condition) throw new Error(message); }
async function check(name, work) {
  try {
    const detail = await work();
    checks.push({ name, status: 'PASS', detail });
    console.log(`PASS ${name}`);
  } catch (error) {
    checks.push({ name, status: 'FAIL', error: error.stack || String(error) });
    console.log(`FAIL ${name}: ${error.message}`);
  }
  fs.writeFileSync(path.join(out, 'checks.json'), JSON.stringify(checks, null, 2));
}

const instruments = [{
  code: '0050', name: '元大台灣50', kind: 'FUND', asOf: '2026-10-09',
  market: 'TWSE', backtestSupported: true,
}];

function iso(date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
function bar(date, periodStart, periodEnd, i, windowStatus) {
  const close = Number((100 + i * 0.11 + Math.sin(i / 3) * 2).toFixed(2));
  const open = Number((close + (i % 2 ? -0.45 : 0.38)).toFixed(2));
  return {
    date: iso(date), periodStart: iso(periodStart), periodEnd: iso(periodEnd),
    observedFrom: iso(date), observedTo: iso(date), periodWindowStatus: windowStatus,
    coverageStatus: 'UNKNOWN', open: open.toFixed(2), high: (Math.max(open, close) + 0.6).toFixed(2),
    low: (Math.min(open, close) - 0.5).toFixed(2), close: close.toFixed(2), volume: 10000 + i * 125,
  };
}
function makeBars(fromText, toText, interval) {
  const from = new Date(`${fromText}T12:00:00+08:00`);
  const to = new Date(`${toText}T12:00:00+08:00`);
  const bars = [];
  let i = 0;
  if (interval === '1d') {
    for (const date = new Date(from); date <= to; date.setDate(date.getDate() + 1)) {
      if (date.getDay() === 0 || date.getDay() === 6) continue;
      bars.push(bar(new Date(date), new Date(date), new Date(date), i++, 'ELAPSED'));
    }
  } else if (interval === '1w') {
    const date = new Date(from);
    date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
    for (; date <= to; date.setDate(date.getDate() + 7)) {
      const end = new Date(date); end.setDate(end.getDate() + 6);
      const observed = new Date(date); observed.setDate(observed.getDate() + 1);
      bars.push(bar(observed, new Date(date), end, i++, date < from || end > to ? 'CLIPPED_BY_REQUEST' : 'ELAPSED'));
    }
  } else {
    const date = new Date(from.getFullYear(), from.getMonth(), 1, 12);
    for (; date <= to; date.setMonth(date.getMonth() + 1)) {
      const end = new Date(date.getFullYear(), date.getMonth() + 1, 0, 12);
      const observed = new Date(date);
      if (observed < from) observed.setTime(from.getTime());
      while (observed.getDay() === 0 || observed.getDay() === 6) observed.setDate(observed.getDate() + 1);
      if (observed > to) continue;
      bars.push(bar(observed, new Date(date), end, i++, date < from || end > to ? 'CLIPPED_BY_REQUEST' : 'ELAPSED'));
    }
  }
  return bars;
}
function syntheticHistory(url, symbol) {
  const from = url.searchParams.get('from');
  const to = url.searchParams.get('to');
  const interval = url.searchParams.get('interval') || '1d';
  const bars = makeBars(from, to, interval);
  return {
    symbol, from, to, observedFrom: bars[0]?.observedFrom || from,
    observedTo: bars.at(-1)?.observedTo || to, fetchedAt: '2026-10-10T09:00:00+08:00',
    source: '合成測試資料（非市場行情）', interval, licensingStatus: 'UNKNOWN',
    adjustmentPolicy: '合成測試價格；不代表市場價格', bars,
    limitations: ['僅供瀏覽器驗收的合成測試資料，非市場行情。', '資料範圍與覆蓋狀態是合成測試值。', '行情授權與公開發布狀態未因此改變。'],
  };
}

const staticServer = http.createServer((request, response) => {
  const pathname = new URL(request.url, webBase).pathname;
  if (pathname === '/config.js') {
    response.setHeader('content-type', 'application/javascript');
    response.end(`window.APP_CONFIG={apiBaseUrl:'${apiBase}',liveMarketDataEnabled:false};`);
    return;
  }
  const filename = path.resolve(dist, `.${pathname === '/' ? '/index.html' : pathname}`);
  if (!filename.startsWith(`${dist}${path.sep}`)) { response.writeHead(403); response.end(); return; }
  fs.readFile(filename, (error, content) => {
    if (error) { response.writeHead(404); response.end('not found'); return; }
    response.setHeader('content-type', ({ '.html': 'text/html', '.js': 'application/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' })[path.extname(filename)] || 'application/octet-stream');
    response.end(content);
  });
});

async function main() {
  if (!fs.existsSync(path.join(dist, 'index.html'))) throw new Error(`Pages build output not found at ${dist}`);
  await new Promise((resolve, reject) => staticServer.once('error', reject).listen(webPort, '127.0.0.1', resolve));
  let browser;
  let context;
  try {
    browser = await chromium.launch({ channel: 'chrome', headless: true, timeout: 30000 });
    context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'zh-TW', timezoneId: 'Asia/Taipei' });
    await context.tracing.start({ screenshots: true, snapshots: true, sources: true });
    const page = await context.newPage();
    page.setDefaultTimeout(10000);
    page.on('pageerror', error => pageErrors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') consoleErrors.push(message.text()); });
    page.on('request', request => { if (!request.url().startsWith(webBase) && !request.url().startsWith(apiBase)) apiCalls.push({ unexpectedExternalRequest: request.url() }); });
    await page.route(`${apiBase}/api/**`, async route => {
      const url = new URL(route.request().url());
      const request = { path: url.pathname, query: url.search };
      apiCalls.push(request);
      if (url.pathname === '/api/v1/instruments') {
        const query = url.searchParams.get('query') || '';
        const items = instruments.filter(item => item.code === query);
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ query, limit: Number(url.searchParams.get('limit') || 20), totalMatches: items.length, items, sources: [], limitations: ['合成測試目錄'] }) });
        return;
      }
      const match = url.pathname.match(/^\/api\/v1\/stocks\/(\d+)\/history$/);
      if (match) {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(syntheticHistory(url, match[1])) });
        return;
      }
      await route.fulfill({ status: 404, contentType: 'application/json', body: '{}' });
    });
    await page.goto(`${webBase}/#/stocks/0050`);
    await page.getByRole('heading', { name: '元大台灣50' }).waitFor();
    await page.locator('.stock-chart-frame svg').waitFor();

    await check('0050 page loads from local synthetic fixture and defaults to daily', async () => {
      const line = await page.locator('.stock-date-line').innerText();
      const summary = await page.locator('.stock-chart-summary').innerText();
      const source = await page.locator('.stock-data-foot').innerText();
      assert(line.includes('請求期間'), `request period is missing: ${line}`);
      assert(summary.includes('日 K'), `daily interval missing: ${summary}`);
      assert(source.includes('合成測試資料（非市場行情）'), `synthetic source disclosure missing: ${source}`);
      assert(apiCalls.some(call => call.path?.endsWith('/history') && call.query?.includes('interval=1d')), 'daily API query was not observed');
      return { line, summary, source, calls: apiCalls.filter(call => call.path?.endsWith('/history')) };
    });
    await page.screenshot({ path: path.join(out, 'daily-desktop.png'), fullPage: true });

    const initialRequest = apiCalls.filter(call => call.path?.endsWith('/history')).at(-1);
    const initialParams = new URLSearchParams(initialRequest.query.slice(1));
    await check('Switching 日K → 週K issues interval=1w and keeps the 1年 range', async () => {
      const count = apiCalls.length;
      await page.getByRole('button', { name: '週 K', exact: true }).click();
      await page.getByText(/根週 K/).waitFor();
      const selected = await page.getByRole('button', { name: '週 K', exact: true }).getAttribute('aria-pressed');
      const request = apiCalls.slice(count).find(call => call.path?.endsWith('/history'));
      assert(selected === 'true', `週K selected state is ${selected}`);
      assert(request?.query.includes('interval=1w'), `weekly request missing interval=1w: ${JSON.stringify(request)}`);
      const params = new URLSearchParams(request.query.slice(1));
      assert(params.get('from') === initialParams.get('from') && params.get('to') === initialParams.get('to'), 'switching interval changed the selected 1-year range');
      assert((await page.locator('.stock-chart-summary').innerText()).includes('週 K'), 'weekly summary label not updated');
      return { selected, request, summary: await page.locator('.stock-chart-summary').innerText() };
    });
    await page.screenshot({ path: path.join(out, 'weekly-desktop.png'), fullPage: true });

    await check('Switching 週K → 月K issues interval=1mo and keeps the 1年 range', async () => {
      const count = apiCalls.length;
      await page.getByRole('button', { name: '月 K', exact: true }).click();
      await page.getByText(/根月 K/).waitFor();
      const selected = await page.getByRole('button', { name: '月 K', exact: true }).getAttribute('aria-pressed');
      const request = apiCalls.slice(count).find(call => call.path?.endsWith('/history'));
      assert(selected === 'true', `月K selected state is ${selected}`);
      assert(request?.query.includes('interval=1mo'), `monthly request missing interval=1mo: ${JSON.stringify(request)}`);
      const params = new URLSearchParams(request.query.slice(1));
      assert(params.get('from') === initialParams.get('from') && params.get('to') === initialParams.get('to'), 'switching interval changed the selected 1-year range');
      assert((await page.locator('.stock-chart-summary').innerText()).includes('月 K'), 'monthly summary label not updated');
      assert((await page.getByRole('button', { name: '1年', exact: true }).getAttribute('aria-pressed')) === 'true', '1年 range selection was lost');
      return { selected, request, summary: await page.locator('.stock-chart-summary').innerText() };
    });
    await page.screenshot({ path: path.join(out, 'monthly-desktop.png'), fullPage: true });

    await check('Display range remains separate from K-line interval', async () => {
      const count = apiCalls.length;
      const responsePromise = page.waitForResponse(response => {
        const url = new URL(response.url());
        return url.pathname.endsWith('/history') && url.searchParams.get('from') !== initialParams.get('from') && url.searchParams.get('interval') === '1mo';
      });
      await page.getByRole('button', { name: '3年', exact: true }).click();
      const response = await responsePromise;
      await page.waitForFunction(() => document.querySelector('.stock-date-line')?.textContent?.includes('2023-10-10'));
      await page.waitForFunction(() => document.querySelector('[aria-label="顯示範圍：1 年、3 年或 5 年"] button.selected')?.textContent?.trim() === '3年');
      const request = apiCalls.slice(count).find(call => call.path?.endsWith('/history'));
      const params = new URLSearchParams(request.query.slice(1));
      assert(params.get('interval') === '1mo', `interval was not preserved: ${request.query}`);
      assert(params.get('from') !== initialParams.get('from'), '3年 range did not change its date request');
      assert((await page.getByRole('button', { name: '3年', exact: true }).getAttribute('aria-pressed')) === 'true', '3年 range selection missing');
      assert((await page.getByRole('button', { name: '月 K', exact: true }).getAttribute('aria-pressed')) === 'true', 'monthly interval selection missing');
      return { request, selectedPeriod: '3年', selectedInterval: '月 K' };
    });

    await check('OHLC synthetic data allows candlestick and line mode toggles', async () => {
      const candleButton = page.getByRole('button', { name: 'K 線', exact: true });
      assert(await candleButton.isEnabled(), 'K線 control is disabled despite fixture OHLC');
      await candleButton.click();
      await page.locator('.candle-body').first().waitFor({ state: 'visible' });
      const candles = await page.locator('.stock-chart-frame .candle-body').count();
      assert(candles > 1, `no candles rendered: count=${candles}`);
      const linePaths = await page.locator('.stock-chart-frame path.chart-line').count();
      assert(linePaths === 0, `line mode still rendered ${linePaths} line paths`);
      await page.getByRole('button', { name: '走勢', exact: true }).click();
      await page.locator('.stock-chart-frame path.chart-line').waitFor({ state: 'visible' });
      assert(await page.locator('.stock-chart-frame path.chart-line').count() === 1, 'line chart did not return after selecting 走勢');
      return { renderedCandles: candles, linePathRestored: true };
    });
    await page.screenshot({ path: path.join(out, 'candlestick-desktop.png'), fullPage: true });

    await check('Keyboard range slider changes the selected historical period', async () => {
      const slider = page.locator('#chart-point-selector');
      const before = await page.locator('.stock-selected-quote').innerText();
      const oldValue = await slider.evaluate(element => element.value);
      await slider.press('ArrowLeft');
      await page.waitForFunction(old => document.querySelector('#chart-point-selector')?.value !== old, oldValue);
      const after = await page.locator('.stock-selected-quote').innerText();
      const valueText = await slider.getAttribute('aria-valuetext');
      const value = await slider.evaluate(element => element.value);
      assert(before !== after, `ArrowLeft did not change the selected quote period: oldValue=${oldValue}; value=${value}; before=${before}; after=${after}; aria=${valueText}`);
      assert(valueText && after.includes(valueText.split('，')[0]), `aria-valuetext is not reflected in selected quote: ${valueText} / ${after}`);
      assert(Number(value) < Number(oldValue), `ArrowLeft did not move slider to an earlier period; old=${oldValue}; new=${value}`);
      return { value, valueText, before, after };
    });

    await page.setViewportSize({ width: 390, height: 844 });
    await check('390px browser viewport remains usable (web viewport only)', async () => {
      const metrics = await page.evaluate(() => ({ innerWidth, documentWidth: document.documentElement.scrollWidth, chartWidth: document.querySelector('.stock-chart-frame')?.getBoundingClientRect().width }));
      await page.screenshot({ path: path.join(out, 'mobile-viewport-only.png'), fullPage: true });
      assert(metrics.documentWidth <= metrics.innerWidth + 1, `horizontal overflow: ${JSON.stringify(metrics)}`);
      assert((await page.getByRole('button', { name: '週 K', exact: true }).count()) === 1, 'weekly control missing at narrow viewport');
      return metrics;
    });

    const tracePath = path.join(out, 'weekly-monthly-browser-trace.zip');
    await context.tracing.stop({ path: tracePath });
    const unexpectedExternal = apiCalls.filter(call => call.unexpectedExternalRequest);
    const environment = {
      evidenceLabels: ['BROWSER_RUNTIME', 'AUTOMATED_TEST'],
      runtimeIdentity: '/root/weekly_monthly_browser_qa',
      browser: { name: 'Google Chrome', version: browser.version(), headless: true },
      target: webBase,
      viewports: [{ width: 1440, height: 1000 }, { width: 390, height: 844, note: 'browser viewport only; not native simulator/device' }],
      syntheticFixture: true,
      syntheticDataDisclosure: '合成測試資料（非市場行情）',
      liveMarketDataEnabled: false,
      historyRequests: apiCalls.filter(call => call.path?.endsWith('/history')),
      unexpectedExternalRequests: unexpectedExternal,
      pageErrors,
      consoleErrors,
      artifacts: ['daily-desktop.png', 'weekly-desktop.png', 'monthly-desktop.png', 'candlestick-desktop.png', 'mobile-viewport-only.png', 'weekly-monthly-browser-trace.zip'],
    };
    fs.writeFileSync(path.join(out, 'environment.json'), JSON.stringify(environment, null, 2));
  } finally {
    if (context) await context.close();
    if (browser) await browser.close();
    staticServer.close();
  }
  const failures = checks.filter(check => check.status !== 'PASS');
  console.log(`${checks.length - failures.length}/${checks.length} checks passed`);
  if (failures.length) process.exitCode = 1;
}

main().catch(error => { console.error(error.stack || error); process.exitCode = 1; });
