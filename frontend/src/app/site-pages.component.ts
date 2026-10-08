import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, ElementRef, inject, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { firstValueFrom } from 'rxjs';

const apiBaseUrl = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');
const strategies = [
  { id: 'ma-crossover', name: '雙均線交叉', group: '趨勢跟隨', rule: '短期均線向上穿越長期均線時買進；反向交叉時賣出。', params: '短均線 20 日 / 長均線 60 日', symbol: '0050', icon: '↗' },
  { id: 'rsi-reversion', name: 'RSI 均值回歸', group: '均值回歸', rule: 'RSI 低於買進門檻時進場，高於賣出門檻時離場。', params: '週期 14 / 買進 30 / 賣出 55', symbol: '2330', icon: '∿' },
  { id: 'bollinger-reversion', name: '布林通道回歸', group: '均值回歸', rule: '收盤低於布林下軌時買進，回到中線時賣出。', params: '週期 20 / 標準差 2 倍', symbol: '0050', icon: '⌁' },
  { id: 'breakout', name: '區間突破', group: '動能突破', rule: '收盤突破前 N 筆高點時買進，跌破均線時賣出。', params: '突破回看 20 筆', symbol: '2330', icon: '↑' },
  { id: 'drawdown-entry', name: '回跌買進 / 獲利出場', group: '自訂規則', rule: '距近一年高點回跌達門檻時買進，持倉報酬達目標時賣出。', params: '回跌 20% / 獲利 20%', symbol: '0056', icon: '↘' },
];

@Component({ selector: 'app-root', standalone: true, imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet], template: `
  <a class="skip-link" href="#main-content" (click)="skipToMain($event)">跳至主要內容</a>
  <header class="site-header" (keydown.escape)="closeMenu($event)">
    <a routerLink="/" class="brand" aria-label="策略實驗室首頁" (click)="closeMenu()"><span class="brand-mark" aria-hidden="true"><i></i><i></i><i></i></span><span>策略<span class="brand-light">實驗室</span></span></a>
    <button #menuToggle class="menu-toggle" type="button" [attr.aria-expanded]="menuOpen" aria-controls="primary-navigation" (click)="menuOpen = !menuOpen"><span>{{ menuOpen ? '關閉選單' : '開啟選單' }}</span><span aria-hidden="true">{{ menuOpen ? '×' : '☰' }}</span></button>
    <nav id="primary-navigation" class="primary-navigation" [class.is-open]="menuOpen" aria-label="主要導覽">
      <a routerLink="/explore" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}" ariaCurrentWhenActive="page" (click)="closeMenu()">探索</a>
      <a routerLink="/strategies" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}" ariaCurrentWhenActive="page" (click)="closeMenu()">策略庫</a>
      <a routerLink="/backtest" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="closeMenu()">回測工作台</a>
      <a routerLink="/methods" routerLinkActive="active" [routerLinkActiveOptions]="{exact:true}" ariaCurrentWhenActive="page" (click)="closeMenu()">方法說明</a>
    </nav><span class="site-status"><i></i>{{ apiBaseUrl ? '台股研究工具' : '研究環境' }}</span>
  </header>
  <main id="main-content" class="route-content" tabindex="-1" aria-label="主要內容"><router-outlet (activate)="focusMain()" /></main>
  <footer class="site-footer"><span>策略實驗室 · 台股歷史研究</span><a routerLink="/methods">資料與方法</a><span>研究用途，非投資建議</span></footer>
` })
export class AppComponent {
  readonly apiBaseUrl = apiBaseUrl;
  menuOpen = false;
  @ViewChild('menuToggle') private menuToggle?: ElementRef<HTMLButtonElement>;
  closeMenu(event?: Event): void {
    if (event) { event.preventDefault(); this.menuOpen = false; this.menuToggle?.nativeElement.focus(); return; }
    this.menuOpen = false;
  }
  skipToMain(event: Event): void { event.preventDefault(); document.getElementById('main-content')?.focus(); }
  focusMain(): void { requestAnimationFrame(() => document.querySelector<HTMLElement>('#main-content h1')?.focus({ preventScroll: true })); }
}

@Component({ selector: 'app-home-page', standalone: true, imports: [CommonModule, FormsModule, RouterLink], template: `
  <section class="home-hero page-wrap">
    <div class="hero-copy"><p class="eyebrow">台股策略研究工作台</p><h1 tabindex="-1">你的投資策略，<br><span>經得起歷史驗證嗎？</span></h1><p class="hero-lede">以明確的資料與成交假設，研究策略、理解風險，再決定下一步。從一個問題開始，不必先讀完一整份說明。</p>
      <form class="home-search" (ngSubmit)="search()"><label for="home-search-input">搜尋股票或 ETF</label><div class="home-search-control"><span aria-hidden="true">⌕</span><input id="home-search-input" name="symbol" [(ngModel)]="symbol" maxlength="30" placeholder="輸入代碼或名稱，例如 2330 / 台積電"><button type="submit" aria-label="搜尋標的">搜尋 →</button></div><span class="home-search-hint">也可以直接使用下方研究範本開始。</span></form>
      <div class="hero-actions"><a class="button-primary" routerLink="/backtest" [queryParams]="{symbol:'0050',strategy:'ma-crossover'}">開始 0050 範例研究 <span aria-hidden="true">→</span></a><a class="button-quiet" routerLink="/explore">瀏覽全部標的</a></div>
      <p class="hero-disclosure">歷史模擬不代表未來績效 · 研究用途，非投資建議</p>
    </div>
    <aside class="hero-card" aria-label="研究流程"><p class="card-kicker">從假設走到理解</p><div class="journey-step"><span>01</span><div><strong>選一個研究問題</strong><small>標的、策略與期間</small></div></div><div class="journey-line"></div><div class="journey-step"><span>02</span><div><strong>檢視績效與風險</strong><small>與相同投入方式比較</small></div></div><div class="journey-line"></div><div class="journey-step"><span>03</span><div><strong>理解計算假設</strong><small>資料、成本與成交模型</small></div></div><a routerLink="/methods" class="text-link">先了解研究方法 <span aria-hidden="true">↗</span></a></aside>
  </section>
  <section class="page-wrap section-block"><div class="section-heading"><div><p class="eyebrow">QUICK START</p><h2>挑一個問題，開始比較</h2></div><a routerLink="/strategies" class="text-link">瀏覽策略庫 →</a></div>
    <div class="template-grid">
      <a class="template-card" routerLink="/backtest" [queryParams]="{symbol:'0050',strategy:'ma-crossover'}"><span class="template-number">研究範本 01</span><strong>0050 定期定額 vs 買進持有</strong><p>比較相同現金流下兩種投入方式，並查看雙均線策略作為第三組對照。</p><span class="template-meta">0050 · 現金流比較 <b>→</b></span></a>
      <a class="template-card" routerLink="/backtest" [queryParams]="{symbol:'2330',strategy:'rsi-reversion'}"><span class="template-number">研究範本 02</span><strong>台積電的 RSI 回歸</strong><p>觀察簡單超買超賣門檻在歷史資料中的表現。</p><span class="template-meta">2330 · RSI 均值回歸 <b>→</b></span></a>
      <a class="template-card" routerLink="/backtest" [queryParams]="{symbol:'0056',strategy:'drawdown-entry'}"><span class="template-number">研究範本 03</span><strong>調整回跌策略參數</strong><p>以現有回跌與獲利規則為起點，調整門檻、投入方式及研究期間。</p><span class="template-meta">既有規則 · 可調參數 <b>→</b></span></a>
    </div>
  </section>
  <section class="page-wrap home-bottom"><div><span class="mini-icon">⌕</span><h2>還不知道從哪裡開始？</h2><p>先搜尋一檔股票，查看目錄資訊與目前資料限制。</p></div><a class="button-outline" routerLink="/explore">探索台股標的</a></section>
` })
export class HomePageComponent {
  private readonly router = inject(Router);
  symbol = '';
  search(): void { const q = this.symbol.trim(); void this.router.navigate(['/explore'], { queryParams: q ? { q } : {} }); }
}

@Component({ selector: 'app-explore-page', standalone: true, imports: [CommonModule, FormsModule, RouterLink], template: `
  <section class="page-wrap subpage"><p class="eyebrow">EXPLORE · 標的目錄</p><h1 tabindex="-1">先找到值得研究的標的。</h1><p class="subpage-lede">搜尋上市公司與基金目錄，了解目前能否帶入回測。出現在目錄中不代表有可用行情。</p>
    <div class="explore-search"><label for="asset-search">搜尋代碼或名稱</label><div class="search-control"><span aria-hidden="true">⌕</span><input id="asset-search" type="search" [(ngModel)]="query" (ngModelChange)="search($event)" (keydown.enter)="$event.preventDefault()" placeholder="例如 2330 或 台積電" autocomplete="off"><span *ngIf="busy" role="status">搜尋中…</span></div><p class="form-hint">至少輸入 2 個字元。目錄只提供標的基本資訊。</p>
    <p *ngIf="error" class="notice-error" role="alert">{{error}}</p><p *ngIf="message" class="notice" role="status">{{message}}</p>
    <div class="explore-results" *ngIf="results"><p class="result-count" role="status" aria-live="polite">找到 {{results.totalMatches}} 筆 · 顯示 {{results.items.length}} 筆</p><article class="asset-row" *ngFor="let item of results.items"><div class="asset-code">{{item.code}}</div><div class="asset-info"><strong>{{item.name}}</strong><span>{{item.kind === 'STOCK' ? '上市公司' : item.kind === 'FUND' ? '基金目錄' : '目錄項目'}} · 出表 {{item.asOf || '未提供'}}</span></div><span class="asset-availability" [class.unavailable]="!compatible(item.code)">{{compatible(item.code) ? '代碼格式可帶入；行情執行時驗證' : '目前格式不支援'}}</span><a *ngIf="compatible(item.code)" [routerLink]="'/backtest'" [queryParams]="{symbol:item.code}" class="small-action">帶入工作台 →</a><span *ngIf="!compatible(item.code)" class="small-action muted">代碼格式不支援</span></article><div class="source-note"><strong>目錄資料與行情分開</strong><p>商品類型與代碼取自公開目錄；實際行情期間、公司行動與完整授權狀態，須在方法說明及回測結果中另行確認。</p><a *ngFor="let source of results.sources" [href]="source.licenseUrl" target="_blank" rel="noopener noreferrer">{{source.provider}} 授權 ↗</a></div></div>
    <div class="explore-empty" *ngIf="!results && !busy"><span aria-hidden="true">⌕</span><strong>從代碼或名稱開始搜尋</strong><p>也可以先從常見研究範本開始。</p><a routerLink="/backtest" [queryParams]="{symbol:'0050',strategy:'ma-crossover'}">前往 0050 均線範本 →</a></div>
  </div>
  <aside class="page-side-note"><strong>目前目錄範圍</strong><p>目前搜尋資料來自 TWSE 公開公司與基金基本資料目錄。搜尋結果不等同完整 ETF 清單，也不保證行情端點支援。</p><a routerLink="/methods">查看資料限制 →</a></aside>
  </section>
` })
export class ExplorePageComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  query = ''; results: any = null; busy = false; error = ''; message = ''; private timer?: ReturnType<typeof setTimeout>; private requestNo = 0;
  private readonly changeDetector = inject(ChangeDetectorRef);
  constructor() {
    const initialQuery = this.route.snapshot.queryParamMap.get('q') ?? '';
    if (initialQuery) { this.query = initialQuery; this.search(initialQuery); }
  }
  search(value: string): void {
    this.query = value.trim().slice(0, 60); this.error = ''; this.message = ''; if (this.timer) clearTimeout(this.timer);
    const id = ++this.requestNo; if (this.query.length < 2) { this.results = null; this.busy = false; return; }
    this.results = null; this.busy = true;
    this.timer = setTimeout(() => void this.load(this.query, id), 300);
  }
  private async load(query: string, id: number): Promise<void> {
    if (!apiBaseUrl) { if (id === this.requestNo) { this.busy = false; this.error = '標的目錄 API 尚未設定；請使用已知代碼或研究範本。'; this.changeDetector.markForCheck(); } return; }
    try { const response = await firstValueFrom(this.http.get<any>(`${apiBaseUrl}/api/v1/instruments`, {params:{query,limit:'20'}})); if (id === this.requestNo) { this.results = response; this.busy = false; this.changeDetector.markForCheck(); } }
    catch (error) { if (id === this.requestNo) { this.busy = false; this.error = error instanceof HttpErrorResponse && error.status === 0 ? '目前無法連線至標的目錄，請稍後重試。' : '標的目錄查詢失敗，請稍後重試。'; this.changeDetector.markForCheck(); } }
  }
  compatible(code: string): boolean { return /^\d{4,6}$/.test(code); }
}

@Component({ selector: 'app-strategies-page', standalone: true, imports: [CommonModule, RouterLink], template: `
  <section class="page-wrap subpage"><p class="eyebrow">STRATEGY LIBRARY · 已實作規則</p><h1 tabindex="-1">策略是可以檢驗的假設。</h1><p class="subpage-lede">先看懂每條規則，再選擇一個標的帶入工作台。這些都是簡化的研究模型，不是投資建議。</p>
    <div class="strategy-grid"><article class="library-card" *ngFor="let item of strategies"><div class="library-card-top"><span class="strategy-symbol">{{item.icon}}</span><span class="strategy-group">{{item.group}}</span></div><h2>{{item.name}}</h2><p>{{item.rule}}</p><div class="params-preview"><span>預設參數</span><strong>{{item.params}}</strong></div><a class="button-outline" [routerLink]="'/backtest'" [queryParams]="{symbol:item.symbol,strategy:item.id}">用 {{item.symbol}} 開始研究 <span aria-hidden="true">→</span></a></article></div>
    <div class="library-footnote"><strong>比較基準</strong><span>每個回測都會與相同現金流設定的定期定額及買進持有方法比較。成交與成本假設詳見方法說明。</span><a routerLink="/methods">了解計算規則 →</a></div>
  </section>
` })
export class StrategiesPageComponent { readonly strategies = strategies; }

@Component({ selector: 'app-methods-page', standalone: true, imports: [CommonModule, RouterLink], template: `
  <section class="page-wrap subpage"><p class="eyebrow">METHODS · 可重現性與限制</p><h1 tabindex="-1">看懂結果，也看懂它怎麼算。</h1><p class="subpage-lede">回測結果依賴資料與假設。開始研究前，先知道模型涵蓋什麼、還缺少什麼。</p>
    <div class="methods-layout"><nav class="methods-index" aria-label="方法說明章節"><a routerLink="/methods" fragment="data">資料與範圍</a><a routerLink="/methods" fragment="execution">訊號與成交</a><a routerLink="/methods" fragment="costs">資金與成本</a><a routerLink="/methods" fragment="metrics">績效指標</a><a routerLink="/methods" fragment="limits">已知限制</a></nav><div class="methods-content">
      <section id="data"><span class="method-no">01</span><div><h2>資料與範圍</h2><p>目前回測以 TWSE 日收盤資料為基礎，行情服務可用期間依各標的實際回應列示。標的目錄只說明基本資料，不代表行情完整性或授權延伸範圍已確認。</p><a href="https://openapi.twse.com.tw/" target="_blank" rel="noopener noreferrer">TWSE OpenAPI ↗</a></div></section>
      <section id="execution"><span class="method-no">02</span><div><h2>訊號與成交</h2><p>指標只使用已完成的日資料產生訊號，並假設於下一筆可用觀察資料的收盤價成交。這是事先定義的日資料代理模型，不是真實市場撮合，也未模擬盤中價格、流動性或滑價。</p></div></section>
      <section id="costs"><span class="method-no">03</span><div><h2>資金與成本</h2><p>研究可設定起始投入、每月投入、手續費及賣出交易稅。最低手續費、券商折扣、融資與股利再投入尚未納入；留白的稅率會依目前代碼規則估算。</p></div></section>
      <section id="metrics"><span class="method-no">04</span><div><h2>績效與風險</h2><p>結果提供期末資產、累計投入、損益、時間加權報酬、年化報酬、年化波動度與每日 TWR 最大回撤。淨投入損益比率會受投入時點影響，不等同時間加權報酬。</p></div></section>
      <section id="limits"><span class="method-no">05</span><div><h2>目前已知限制</h2><ul><li>未處理股利、分割及完整公司行動帳務。</li><li>資料日曆與缺漏補值政策尚待驗證。</li><li>公開目錄授權不代表行情或衍生結果可任意保存、分享或再發布。</li><li>瀏覽器只保存研究設定，不保存行情或回測結果。</li></ul><p>因此，結果適合用來檢視假設與建立研究問題，不應視為完整的投資績效紀錄。</p></div></section>
    </div></div><div class="methods-cta"><div><strong>帶著清楚的假設開始研究</strong><p>挑選策略與標的，結果中再逐項核對資料期間及執行假設。</p></div><a class="button-primary" routerLink="/backtest" [queryParams]="{symbol:'0050',strategy:'ma-crossover'}">前往回測工作台 →</a></div>
  </section>
` })
export class MethodsPageComponent {}

@Component({ selector: 'app-not-found-page', standalone: true, imports: [RouterLink], template: `<section class="page-wrap subpage not-found"><p class="eyebrow">404 · 找不到頁面</p><h1 tabindex="-1">這個研究入口不存在。</h1><p class="subpage-lede">請從已提供的導覽選擇一個研究任務。</p><a class="button-primary" routerLink="/">回到研究首頁 →</a></section>` })
export class NotFoundPageComponent {}
