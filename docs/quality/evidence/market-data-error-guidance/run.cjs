// Local browser QA fixture server. All responses are synthetic and are not market data.
// Run from the repository root: node docs/quality/evidence/market-data-error-guidance/run.cjs
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { URL } = require('node:url');

const root = path.resolve('frontend/dist/pages/browser');
const evidence = path.resolve('docs/quality/evidence/market-data-error-guidance');
const port = Number(process.env.PORT || 8877);
let backtestCount = 0;
let robustnessCount = 0;
const apiRequests = [];
const externalRequests = [];
const sampleInstruments = [
  { code: '0050', name: '合成台灣50', kind: 'FUND', market: 'TWSE', asOf: '2026-10-01', backtestSupported: true },
  { code: '0051', name: '合成科技基金', kind: 'FUND', market: 'TWSE', asOf: '2026-10-01' },
  { code: '0052', name: '合成產業基金', kind: 'FUND', market: 'TWSE', asOf: '2026-10-01', backtestSupported: false },
];
const source = {
  title: '本機 QA 合成目錄', provider: 'Synthetic Fixture', updateFrequency: '測試固定資料',
  asOfFrom: '2026-10-01', asOfTo: '2026-10-01', fetchedAt: '2026-10-10T00:00:00Z',
  cacheTtlSeconds: 0, license: '非行情；僅供本機測試', licenseUrl: 'http://localhost:8877/fixture-license', datasetUrl: 'http://localhost:8877/fixture-data',
};
const codes = ['NO_MARKET_DATA', 'UPSTREAM_DATA_UNAVAILABLE', 'DATA_INTEGRITY_FAILED', 'INVALID_INPUT'];
const messages = [
  'synthetic body for no market data', 'synthetic upstream failure',
  'synthetic integrity failure', 'synthetic invalid input',
];

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' });
  res.end(JSON.stringify(body));
}
function readBody(req) { return new Promise((resolve) => { let body = ''; req.on('data', (chunk) => body += chunk); req.on('end', () => resolve(body)); }); }
function record(req, url, extra = {}) { apiRequests.push({ at: new Date().toISOString(), method: req.method, path: url.pathname, query: url.searchParams.toString(), ...extra }); }
function baseMetrics(value = 1) { return { totalReturnPercent: value, annualizedReturnPercent: value, maxDrawdownPercent: -value, annualizedRealizedVolatilityPercent: value }; }
const range = { min: 0.1, median: 0.2, max: 0.3 };

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${port}`);
  if (url.pathname === '/__qa/state') {
    json(res, 200, { fixtureOnly: true, liveMarketDataEnabled: false, apiRequests, externalRequests, backtestCount, robustnessCount });
    return;
  }
  if (url.pathname === '/config.js') {
    res.writeHead(200, { 'content-type': 'application/javascript; charset=utf-8', 'cache-control': 'no-store' });
    res.end(`window.APP_CONFIG = { apiBaseUrl: 'http://127.0.0.1:${port}', liveMarketDataEnabled: false }; window.__QA_FIXTURE_ONLY__ = true;`);
    return;
  }
  if (url.pathname === '/api/v1/instruments') {
    record(req, url);
    const query = (url.searchParams.get('query') || '').trim().toLowerCase();
    const items = sampleInstruments.filter((item) => !query || `${item.code} ${item.name}`.toLowerCase().includes(query));
    json(res, 200, { items, totalMatches: items.length, sources: [source], limitations: ['本機合成 fixture；不是 TWSE/Yahoo 或任何真實行情。'] });
    return;
  }
  if (url.pathname === '/api/v1/backtests' && req.method === 'POST') {
    const body = await readBody(req);
    const outcome = backtestCount++;
    record(req, url, { outcome, body: JSON.parse(body || '{}') });
    if (outcome < 4) return json(res, outcome === 1 ? 503 : 422, { code: codes[outcome], message: messages[outcome] });
    if (outcome === 4) return json(res, 500, { error: 'SECRET-ISH raw arbitrary server detail should never be shown to users', debug: 'synthetic raw body' });
    if (outcome === 5) { res.destroy(); return; }
    return json(res, 500, { code: 'FIXTURE_DONE', message: 'synthetic fixture complete' });
  }
  if (url.pathname === '/api/v1/robustness-matrices' && req.method === 'POST') {
    const body = await readBody(req);
    const outcome = robustnessCount++;
    record(req, url, { outcome, body: JSON.parse(body || '{}') });
    if (outcome === 0) return json(res, 200, {
      baseId: 'synthetic-base', symbol: '0050', requestedFrom: '2020-01-01', requestedTo: '2021-01-01',
      observedFrom: '2020-01-02', observedTo: '2020-12-31', sampleCount: 3, status: 'SYNTHETIC_FIXTURE',
      dataSource: '本機合成資料（非市場行情）', limitations: ['僅為瀏覽器驗收 fixture。'],
      cases: ['short-window-15', 'short-window-25', 'long-window-50', 'long-window-70', 'higher-cost'].map((id) => ({ id, sampleCount: 3, metrics: baseMetrics() })),
      aggregate: { totalReturnPercent: range, annualizedReturnPercent: range, maxDrawdownPercent: range, annualizedRealizedVolatilityPercent: range },
    });
    return json(res, 500, { message: 'PRIVATE DEBUG RAW BODY must stay hidden', trace: 'synthetic robustness failure' });
  }
  if (url.pathname.startsWith('/api/')) {
    record(req, url);
    return json(res, 404, { code: 'FIXTURE_NOT_FOUND', message: 'No local synthetic fixture for this API route.' });
  }
  let decoded;
  try { decoded = decodeURIComponent(url.pathname); } catch { decoded = '/'; }
  const requested = path.resolve(root, `.${decoded === '/' ? '/index.html' : decoded}`);
  if (!requested.startsWith(root + path.sep) && requested !== path.join(root, 'index.html')) {
    res.writeHead(403); res.end('Forbidden'); return;
  }
  const file = fs.existsSync(requested) && fs.statSync(requested).isFile() ? requested : path.join(root, 'index.html');
  const type = file.endsWith('.js') ? 'application/javascript' : file.endsWith('.css') ? 'text/css' : file.endsWith('.svg') ? 'image/svg+xml' : 'text/html';
  fs.readFile(file, (error, bytes) => {
    if (error) { res.writeHead(404); res.end('Not found'); return; }
    res.writeHead(200, { 'content-type': `${type}; charset=utf-8`, 'cache-control': 'no-store' });
    res.end(bytes);
  });
});
server.listen(port, '127.0.0.1', () => {
  console.log(JSON.stringify({ event: 'fixture-server-ready', port, root, liveMarketDataEnabled: false, fixtureOnly: true }));
  fs.writeFileSync(path.join(evidence, 'fixture-server-start.json'), JSON.stringify({ startedAt: new Date().toISOString(), port, root, liveMarketDataEnabled: false, fixtureOnly: true }, null, 2));
});
process.on('SIGINT', () => {
  fs.writeFileSync(path.join(evidence, 'fixture-server-requests.json'), JSON.stringify({ apiRequests, externalRequests, fixtureOnly: true }, null, 2));
  server.close(() => process.exit(0));
});
