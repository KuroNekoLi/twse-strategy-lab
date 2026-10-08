const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const http = require('node:http');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');
const { chromium } = require('/Users/linli/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const root = '/Users/linli/dev_spring-boot/twse-strategy-lab';
const out = path.join(root, 'docs/quality/evidence/m1-browser-catalog');
const apiPort = 14202, upstreamPort = 19082, webPort = 18082;
const api = `http://127.0.0.1:${apiPort}`, web = `http://127.0.0.1:${webPort}`;
const jar = path.join(root, 'backend/target/twse-strategy-lab-api-1.0.0.jar');
const dist = path.join(root, 'frontend/dist/pages/browser');
const java = '/opt/homebrew/opt/openjdk@17/bin/java';
const checks = [];
const requests = [];
function hash(p) { return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'); }
function ensure(x, m) { if (!x) throw new Error(m); }
function fixtureCompany() { return [
 { '公司代號':'2330', '公司名稱':'台灣積體電路製造股份有限公司', '公司簡稱':'台積電', '出表日期':'1151008' },
 { '公司代號':'2308', '公司名稱':'台達電子工業股份有限公司', '公司簡稱':'台達電', '出表日期':'1151008' },
 { '公司代號':'1301', '公司名稱':'台灣塑膠工業股份有限公司', '公司簡稱':'台塑', '出表日期':'1151008' },
 { '公司代號':'2603', '公司名稱':'長榮海運股份有限公司', '公司簡稱':'長榮', '出表日期':'1151008' },
 { '公司代號':'2882', '公司名稱':'國泰金融控股股份有限公司', '公司簡稱':'國泰金', '出表日期':'1151008' }
]; }
function fixtureFund() { return [
 { '基金代號':'00400A', '基金簡稱':'合成主動基金A', '出表日期':'1151008' },
 { '基金代號':'0050', '基金簡稱':'元大台灣50', '出表日期':'1151008' },
 { '基金代號':'0051', '基金簡稱':'元大中型100', '出表日期':'1151008' },
 { '基金代號':'0056', '基金簡稱':'高股息基金', '出表日期':'1151008' }
]; }
function fixtureTpex() { return [
 { Date:'1151008', SecuritiesCompanyCode:'1240', CompanyName:'台灣測試股份有限公司', CompanyAbbreviation:'台灣測試', Chairman:'PRIVATE_PERSON' }
]; }
async function listen(server, port) { await new Promise((resolve, reject) => server.once('error', reject).listen(port, '127.0.0.1', resolve)); }
async function check(name, fn) { try { const detail = await fn(); checks.push({name,status:'PASS',detail}); } catch(e) { checks.push({name,status:'FAIL',error:e.stack || String(e)}); throw e; } }
(async () => {
 let backend, browser; const log = fs.openSync(path.join(out,'backend.log'),'w');
 const upstream = http.createServer(async (req,res) => {
   const u = new URL(req.url, `http://127.0.0.1:${upstreamPort}`); requests.push({path:u.pathname,at:new Date().toISOString()});
   await new Promise(r=>setTimeout(r,180)); res.setHeader('Content-Type','application/json');
   if (u.pathname === '/company') { res.end(JSON.stringify(fixtureCompany())); return; }
   if (u.pathname === '/fund') { res.end(JSON.stringify(fixtureFund())); return; }
   if (u.pathname === '/tpex') { res.end(JSON.stringify(fixtureTpex())); return; }
   if (u.pathname === '/STOCK_DAY') { res.end(JSON.stringify({stat:'OK',data:[]})); return; }
   res.writeHead(404);res.end('[]');
 });
 const frontend = http.createServer((req,res)=>{
   const u = new URL(req.url,web);
   if (u.pathname==='/config.js') {res.setHeader('Content-Type','application/javascript');res.end(`window.APP_CONFIG={apiBaseUrl:'${api}'};`);return;}
   const file=path.resolve(dist,'.'+(u.pathname==='/'?'/index.html':u.pathname));
   if(!file.startsWith(dist+path.sep)){res.writeHead(403);res.end();return;}
   fs.readFile(file,(e,b)=>{if(e){res.writeHead(404);res.end();return;} const types={'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml'};res.setHeader('Content-Type',types[path.extname(file)]||'application/octet-stream');res.end(b);});
 });
 try {
   for (const port of [apiPort,webPort,upstreamPort]) { const s=http.createServer(); await listen(s,port); await new Promise(r=>s.close(r)); }
   await listen(upstream,upstreamPort); await listen(frontend,webPort);
   backend=spawn(java,['-jar',jar,'--spring.profiles.active=local','--server.address=127.0.0.1',`--server.port=${apiPort}`,`--app.twse.base-url=http://127.0.0.1:${upstreamPort}/STOCK_DAY`,`--app.catalog.company-url=http://127.0.0.1:${upstreamPort}/company`,`--app.catalog.fund-url=http://127.0.0.1:${upstreamPort}/fund`,`--app.catalog.tpex-company-url=http://127.0.0.1:${upstreamPort}/tpex`,`--app.cors.allowed-origin-patterns=${web}`],{cwd:root,stdio:['ignore',log,log]});
   for(let i=0;i<80;i++){try{const r=await fetch(`${api}/api/v1/instruments?query=2330&limit=20`);if(r.status>0)break;}catch{} if(backend.exitCode!==null)throw new Error('Backend exited early');await new Promise(r=>setTimeout(r,500));}
   const browserMeta = spawnSync('/usr/bin/sw_vers',['-productVersion'],{encoding:'utf8'}).stdout.trim();
   console.log('Backend ready; launching Google Chrome');
   browser=await chromium.launch({channel:'chrome',headless:true,timeout:20000}); console.log('Chrome launched; running catalog scenarios');
   const context=await browser.newContext({viewport:{width:1440,height:1100},locale:'zh-TW',timezoneId:'Asia/Taipei'});
   await context.tracing.start({screenshots:true,snapshots:true,sources:true});
   const page=await context.newPage(); const consoleErrors=[]; const net=[];
   page.on('request',r=>net.push({kind:'request',url:r.url()}));page.on('response',r=>net.push({kind:'response',url:r.url(),status:r.status()}));
   page.on('pageerror',e=>consoleErrors.push(e.message)); page.on('console',m=>{if(m.type()==='error')consoleErrors.push(m.text());});
   await page.goto(`${web}/#/explore`); await page.getByLabel('搜尋代碼或名稱').waitFor();
   const exploreSearch=page.getByLabel('搜尋代碼或名稱');
   await check('Explore page searches public directory and shows market/source availability',async()=>{await exploreSearch.fill('2330');const row=page.locator('.asset-row').filter({hasText:'2330'});await row.waitFor();ensure((await row.innerText()).includes('台積電'),'company short name missing');ensure((await row.innerText()).includes('上市'),'TWSE market missing');ensure((await row.innerText()).includes('可帶入回測'),'supported listing availability missing');await page.screenshot({path:path.join(out,'explore-company-search.png'),fullPage:true});return {query:'2330',row:await row.innerText()};});
   await check('Explore accepts one-character Chinese name search',async()=>{await exploreSearch.fill('台');const rows=page.locator('.asset-row');for(const name of ['台積電','台達電','台塑'])await rows.filter({hasText:name}).waitFor();return {query:'台',results:(await rows.allInnerTexts()).slice(0,10)};});
   await check('Explore page labels OTC as searchable but unsupported for current backtest',async()=>{await exploreSearch.fill('1240');const row=page.locator('.asset-row').filter({hasText:'1240'});await row.waitFor();ensure((await row.innerText()).includes('上櫃'),'OTC market missing');ensure((await row.innerText()).includes('目前回測行情來源尚未支援'),'backtest limitation missing');ensure(await row.getByText('尚未支援',{exact:true}).count()===1,'unsupported action is unclear');return {query:'1240',row:await row.innerText()};});
   await page.goto(`${web}/#/backtest`); await page.getByLabel('搜尋代碼或名稱').waitFor();
   const search=page.getByLabel('搜尋代碼或名稱');
   await check('Code search, transient loading, rendered result and company label',async()=>{await search.fill('2330');await page.getByText('搜尋中…').waitFor();const row=page.locator('.catalog-results > ul > li').filter({hasText:'2330'});await row.waitFor();await page.getByText('台積電',{exact:true}).waitFor();ensure((await row.innerText()).includes('上市公司'),'company kind label incorrect');ensure(await page.getByText('搜尋中…').count()===0,'loading indicator remained after API success');await page.screenshot({path:path.join(out,'company-search-attribution.png'),fullPage:true});return {query:'2330',row:await row.innerText(),loadingCleared:true};});
   await check('Prefix code search returns 0050 and 0051',async()=>{await search.fill('005');const rows=page.locator('.catalog-results > ul > li');await rows.filter({hasText:'0050'}).waitFor();await rows.filter({hasText:'0051'}).waitFor();return {query:'005',results:await rows.allInnerTexts()};});
   await check('Chinese name search prioritizes common stock names',async()=>{await search.fill('台');const rows=page.locator('.catalog-results > ul > li');for(const name of ['台積電','台達電','台塑'])await rows.filter({hasText:name}).waitFor();return {query:'台',results:(await rows.allInnerTexts()).slice(0,10)};});
   await check('Source/license attribution and limitations',async()=>{await page.getByText('目錄來源與限制',{exact:true}).click();const provenance=await page.locator('.catalog-provenance').innerText();for(const s of ['上市公司基本資料','基金基本資料','上櫃公司基本資料','OGDL v1.0','MONTHLY','DAILY','出表日期','取得時間','900 秒','未將所有基金推定為 ETF','未回傳公司聯絡資料'])ensure(provenance.includes(s),`missing provenance: ${s}`);const links=await page.locator('.catalog-source a').evaluateAll(as=>as.map(a=>({text:a.textContent,href:a.href})));return {provenance:provenance.slice(0,1200),links};});
   await check('Duplicate is disabled with clear status',async()=>{await search.fill('0050');const row=page.locator('.catalog-results > ul > li').filter({hasText:'0050'}).first();await row.waitFor();const button=row.getByRole('button');ensure(await button.isDisabled(),'duplicate add enabled');ensure((await button.innerText()).includes('已加入'),'duplicate status missing');return {button:await button.innerText(),disabled:await button.isDisabled()};});
   await check('Name search and selectable 4–6 digit code',async()=>{await search.fill('長榮');const row=page.locator('.catalog-results > ul > li').filter({hasText:'2603'});await row.waitFor();const before=await row.innerText();ensure(before.includes('長榮'),'name result missing');const button=row.getByRole('button');ensure(!(await button.isDisabled()),`numeric symbol unexpectedly disabled: ${before}`);await button.click();await page.waitForTimeout(300);const selectedText=await page.locator('.selected-symbols').innerText();await page.screenshot({path:path.join(out,'selected-company.png'),fullPage:true});ensure(selectedText.includes('2603'),`selected code not shown: ${selectedText}; message=${await page.locator('.catalog-message').innerText()}`);ensure((await page.locator('.catalog-message').innerText()).includes('已加入比較清單'),'added confirmation missing');return {selected:selectedText,before};});
   await page.screenshot({path:path.join(out,'selected-company.png'),fullPage:true});
   await check('OTC item is searchable but unavailable to TWSE backtest',async()=>{await search.fill('1240');const row=page.locator('.catalog-results > ul > li').filter({hasText:'1240'});await row.waitFor();ensure((await row.innerText()).includes('上櫃公司'),'OTC market label missing');const button=row.getByRole('button');ensure(await button.isDisabled(),'unsupported OTC item was selectable');ensure((await row.innerText()).includes('目前回測行情來源尚未支援'),'missing backtest limitation');return {row:await row.innerText(),disabled:await button.isDisabled()};});
   await check('Alphanumeric fund is disabled with incompatibility explanation',async()=>{await search.fill('00400A');const row=page.locator('.catalog-results > ul > li').filter({hasText:'00400A'});await row.waitFor();ensure((await row.innerText()).includes('基金目錄'),'fund label missing');const button=row.getByRole('button');ensure(await button.isDisabled(),'alphanumeric code button enabled');ensure((await row.innerText()).includes('4 至 6 位數字代碼'),'compatibility reason missing');const source=await page.locator('.catalog-provenance').innerText();return {row:await row.innerText(),disabled:await button.isDisabled()};});
   await check('Maximum count is enforced',async()=>{await search.fill('2882');const row=page.locator('.catalog-results > ul > li').filter({hasText:'2882'});await row.waitFor();await row.getByRole('button').click();await search.fill('2330');const selected=page.locator('.catalog-results > ul > li').filter({hasText:'2330'}).getByRole('button');ensure(await selected.isDisabled(),'fourth code unexpectedly enabled');ensure((await selected.innerText()).includes('已達上限'),'max status absent');return {selected:await page.locator('.selected-symbols').innerText(),fourthButton:await selected.innerText()};});
   await check('Empty result state',async()=>{await search.fill('NO-MATCH');await page.getByText('沒有符合的目錄項目。',{exact:true}).waitFor();return {empty:await page.locator('.catalog-empty').innerText()};});
   await check('Error state is visible and stale results are cleared',async()=>{
     await page.route('**/api/v1/instruments?query=FAIL**',route=>route.fulfill({status:503,contentType:'application/json',body:'{"error":"synthetic failure"}'}));
     await search.fill('2603');await page.locator('.catalog-results > ul > li').waitFor();
     await search.fill('FAIL');await page.getByRole('alert').filter({hasText:'查詢失敗'}).waitFor();
     ensure(await page.locator('.catalog-results').count()===0,'stale result remains visible after error');
     return {error:await page.getByRole('alert').innerText(),staleResults:await page.locator('.catalog-results').count()};
   });
   await check('Out-of-order old response cannot replace current results',async()=>{
     await page.unrouteAll();
     await page.route('**/api/v1/instruments**',async(route)=>{
       const q=new URL(route.request().url()).searchParams.get('query');
       if(q==='SLOW') {await new Promise(r=>setTimeout(r,900));await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({query:q,limit:20,totalMatches:1,items:[{code:'2882',name:'舊查詢結果',kind:'STOCK',asOf:'2026-10-08'}],sources:[],limitations:[]})});}
       else if(q==='FAST') await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({query:q,limit:20,totalMatches:1,items:[{code:'2603',name:'新查詢結果',kind:'STOCK',asOf:'2026-10-08'}],sources:[],limitations:[]})});
       else await route.continue();
     });
     await search.fill('SLOW');await page.waitForTimeout(450);await search.fill('FAST');await page.getByText('新查詢結果',{exact:true}).waitFor();await page.waitForTimeout(1000);
     ensure(await page.getByText('新查詢結果',{exact:true}).count()===1,'new response disappeared');ensure(await page.getByText('舊查詢結果',{exact:true}).count()===0,'stale slow response replaced current results');
     return {query:await page.locator('.catalog-results').innerText()};
   });
   await page.screenshot({path:path.join(out,'latest-query-result.png'),fullPage:true});
   await context.tracing.stop({path:path.join(out,'catalog-trace.zip')});
   const expectedConsoleErrors=consoleErrors.filter(e=>e==='Failed to load resource: the server responded with a status of 503 (Service Unavailable)');
   const unexpectedConsoleErrors=consoleErrors.filter(e=>!expectedConsoleErrors.includes(e));
   const env={evidenceLabels:['BROWSER_RUNTIME','AUTOMATED_TEST'],runtimeIdentity:'/root/catalog-db-browser-qa',taskId:'CATALOG-DB-SEARCH',browser:{name:'Google Chrome',version:browser.version(),headless:true},host:{os:os.platform(),release:os.release(),macOS:browserMeta,arch:os.arch()},viewport:{width:1440,height:1100},target:web,backend:api,syntheticUpstream:`http://127.0.0.1:${upstreamPort}`,fixtureOnly:true,liveMarketData:false,artifactHashes:Object.fromEntries(['company-search-attribution.png','selected-company.png','latest-query-result.png','catalog-trace.zip'].map(f=>[f,hash(path.join(out,f))])),upstreamRequests:requests,expectedConsoleErrors,unexpectedConsoleErrors};
   fs.writeFileSync(path.join(out,'environment.json'),JSON.stringify(env,null,2));fs.writeFileSync(path.join(out,'checks.json'),JSON.stringify(checks,null,2));
   if(unexpectedConsoleErrors.length) throw new Error(`Unexpected browser console/page errors: ${unexpectedConsoleErrors.join(' | ')}`);
 } finally { if(browser)await browser.close();if(backend)backend.kill('SIGTERM');upstream.close();frontend.close();fs.closeSync(log); }
 const failed=checks.filter(c=>c.status!=='PASS');console.log(`${checks.length-failed.length}/${checks.length} PASS`,checks.map(c=>`${c.status} ${c.name}`).join('\n'));if(failed.length)process.exitCode=1;
})().catch(e=>{console.error(e.stack||e);process.exitCode=1;});
