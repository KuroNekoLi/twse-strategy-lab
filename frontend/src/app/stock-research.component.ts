import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';

type Instrument = { code: string; name: string; kind: string; asOf: string; market: string; backtestSupported: boolean };
type PricePoint = { date: string; close: string };
type History = { symbol: string; from: string; to: string; observedFrom: string; observedTo: string; fetchedAt: string; source: string; interval: string; licensingStatus: string; adjustmentPolicy: string; bars: PricePoint[]; limitations: string[] };
const apiBaseUrl = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');

@Component({
  selector: 'app-stock-research-page', standalone: true, imports: [CommonModule, RouterLink],
  template: `
    <section class="page-wrap subpage stock-research-page">
      <a class="stock-back-link" routerLink="/explore">← 回到標的探索</a>
      <p class="eyebrow">STOCK RESEARCH · 個股研究檔案</p>
      <ng-container *ngIf="instrument as item; else loadingInstrument">
        <div class="stock-heading"><div><h1 tabindex="-1">{{item.name}}</h1><p class="stock-identity">{{item.code}} <span>·</span> {{item.market === 'TWSE' ? '臺灣證券交易所上市' : '市場未確認'}}</p></div><span class="stock-status"><i></i>歷史資料研究</span></div>
        <p class="subpage-lede">從這檔標的開始，查看可取得的歷史收盤趨勢，再把研究問題帶入策略驗證。</p>

        <div *ngIf="error" class="stock-error" role="alert"><strong>目前無法載入走勢</strong><span>{{error}}</span><button type="button" (click)="loadHistory()">重新載入</button></div>
        <div *ngIf="busy" class="stock-loading" role="status">正在取得歷史收盤資料…</div>
        <ng-container *ngIf="history as data">
          <section class="stock-chart-card" aria-labelledby="stock-chart-title">
            <div class="stock-chart-heading"><div><p class="eyebrow">PRICE HISTORY · 日收盤</p><h2 id="stock-chart-title">{{latestClose | number:'1.2-2'}} <small>元</small></h2><p class="stock-date-line">觀察區間 {{data.observedFrom}} – {{data.observedTo}}</p></div><div class="stock-periods" role="group" aria-label="走勢顯示期間"><button *ngFor="let period of periods" type="button" [disabled]="busy" [class.selected]="selectedPeriod === period" [attr.aria-pressed]="selectedPeriod === period" (click)="changePeriod(period)">{{period}}</button></div></div>
            <div class="stock-chart-summary"><span [class.negative]="periodChange < 0">{{periodChange >= 0 ? '+' : ''}}{{periodChange | number:'1.2-2'}}%</span><span>區間變化</span><span class="chart-summary-divider"></span><span>{{data.bars.length}} 個交易觀察</span></div>
            <div class="stock-chart-frame" *ngIf="points.length > 1; else noPoints">
              <svg viewBox="0 0 900 300" role="img" [attr.aria-label]="item.name + '每日收盤價趨勢圖，期間 ' + data.observedFrom + ' 至 ' + data.observedTo">
                <defs><linearGradient id="stock-fill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#1c9b83" stop-opacity=".2"/><stop offset="100%" stop-color="#1c9b83" stop-opacity="0"/></linearGradient></defs>
                <g class="chart-grid"><line x1="58" y1="34" x2="884" y2="34"/><line x1="58" y1="110" x2="884" y2="110"/><line x1="58" y1="186" x2="884" y2="186"/><line x1="58" y1="262" x2="884" y2="262"/></g>
                <g class="chart-axis-labels"><text x="4" y="39">{{priceTicks[0] | number:'1.0-0'}}</text><text x="4" y="115">{{priceTicks[1] | number:'1.0-0'}}</text><text x="4" y="191">{{priceTicks[2] | number:'1.0-0'}}</text><text x="4" y="267">{{priceTicks[3] | number:'1.0-0'}}</text><text x="58" y="292">{{data.observedFrom}}</text><text x="884" y="292" text-anchor="end">{{data.observedTo}}</text></g>
                <path class="chart-area" [attr.d]="areaPath"/><path class="chart-line" [attr.d]="linePath"/><circle class="chart-current" [attr.cx]="points[points.length - 1].x" [attr.cy]="points[points.length - 1].y" r="5"/>
              </svg>
              <p class="sr-only">走勢摘要：起始收盤 {{firstClose | number:'1.2-2'}} 元，期間最高 {{highClose | number:'1.2-2'}} 元，最低 {{lowClose | number:'1.2-2'}} 元，最新收盤 {{latestClose | number:'1.2-2'}} 元，區間變化 {{periodChange | number:'1.2-2'}}%。</p>
            </div>
            <ng-template #noPoints><div class="stock-no-points">此期間沒有足夠資料繪製走勢。</div></ng-template>
            <div class="stock-data-foot"><span>來源：{{data.source}}</span><span>本次取得：{{data.fetchedAt}}</span><span>未調整原始收盤價 · 非即時</span></div>
          </section>
          <section class="stock-next-step"><div><p class="eyebrow">CONTINUE YOUR RESEARCH</p><h2>把觀察變成可檢驗的問題</h2><p>選擇策略與期間，檢視歷史表現及計算假設。</p></div><a class="button-primary" [routerLink]="'/backtest'" [queryParams]="{symbol:item.code}">用 {{item.code}} 開始回測 <span aria-hidden="true">→</span></a></section>
          <section class="stock-data-notice"><div class="notice-mark" aria-hidden="true">i</div><div><strong>資料範圍與限制</strong><p>目前圖表只呈現日收盤價，沒有 OHLC K 線、成交量、股利或公司行動資料。缺漏、停牌與上市生命週期狀態尚未驗證；歷史行情授權及衍生圖表展示權尚待確認，因此公開發布狀態仍為 BLOCKED。</p><a href="https://openapi.twse.com.tw/" target="_blank" rel="noopener noreferrer">查看 TWSE OpenAPI ↗</a></div></section>
        </ng-container>
      </ng-container>
      <ng-template #loadingInstrument><div *ngIf="!error" class="stock-loading" role="status">正在確認標的資訊…</div><div *ngIf="error" class="stock-error" role="alert"><strong>找不到可用的上市標的資訊</strong><span>{{error}}</span><a routerLink="/explore">返回標的探索</a></div></ng-template>
    </section>
  `,
})
export class StockResearchPageComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  readonly periods = ['1年', '3年', '5年'];
  symbol = '';
  instrument: Instrument | null = null;
  history: History | null = null;
  selectedPeriod = '1年';
  busy = false;
  error = '';
  points: { x: number; y: number }[] = [];
  priceTicks: number[] = [0, 0, 0, 0];
  linePath = '';
  areaPath = '';
  latestClose = 0;
  firstClose = 0;
  highClose = 0;
  lowClose = 0;
  periodChange = 0;
  private historyRequestId = 0;
  private instrumentRequestId = 0;

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.symbol = params.get('symbol') ?? '';
      this.instrument = null; this.history = null; this.busy = false; this.error = '';
      this.historyRequestId += 1;
      const requestId = ++this.instrumentRequestId;
      if (!/^\d{4,6}$/.test(this.symbol)) { this.error = '標的代碼格式不正確。'; this.cdr.markForCheck(); return; }
      void this.loadInstrument(this.symbol, requestId);
    });
  }

  changePeriod(period: string): void { this.selectedPeriod = period; void this.loadHistory(); }

  async loadInstrument(symbol: string, requestId: number): Promise<void> {
    if (!apiBaseUrl) { this.error = '研究 API 尚未設定。'; return; }
    this.error = '';
    try {
      const result = await firstValueFrom(this.http.get<{items: Instrument[]}>(`${apiBaseUrl}/api/v1/instruments`, { params: { query: symbol, limit: '20' } }));
      if (requestId !== this.instrumentRequestId || symbol !== this.symbol) return;
      this.instrument = result.items.find((item) => item.code === symbol && item.market === 'TWSE' && item.backtestSupported) ?? null;
      if (!this.instrument) { this.error = '目前只提供已確認支援的上市標的研究頁；此代碼尚未列入可用範圍。'; return; }
      await this.loadHistory();
    } catch (failure) { if (requestId === this.instrumentRequestId && symbol === this.symbol) this.error = this.messageFor(failure); }
    finally { this.cdr.markForCheck(); }
  }

  async loadHistory(): Promise<void> {
    if (!this.instrument || !apiBaseUrl) return;
    const requestId = ++this.historyRequestId;
    this.busy = true; this.error = ''; this.cdr.markForCheck();
    const to = new Date(); const from = new Date(to); from.setFullYear(to.getFullYear() - Number.parseInt(this.selectedPeriod, 10));
    try {
      const data = await firstValueFrom(this.http.get<History>(`${apiBaseUrl}/api/v1/stocks/${this.symbol}/history`, { params: { from: this.dateParam(from), to: this.dateParam(to) } }));
      if (requestId === this.historyRequestId) { this.history = data; this.prepareChart(data.bars); }
    } catch (failure) { if (requestId === this.historyRequestId) { this.error = this.messageFor(failure); this.history = null; } }
    finally { if (requestId === this.historyRequestId) { this.busy = false; this.cdr.markForCheck(); } }
  }

  private prepareChart(bars: PricePoint[]): void {
    const values = bars.map((bar) => Number(bar.close)).filter(Number.isFinite);
    if (!values.length) { this.points = []; return; }
    const min = Math.min(...values), max = Math.max(...values), spread = max - min || Math.max(max * .02, 1), low = min - spread * .08, high = max + spread * .08;
    this.priceTicks = [high, high - (high - low) / 3, high - (high - low) * 2 / 3, low];
    this.points = values.map((value, index) => ({ x: 58 + index * 826 / Math.max(values.length - 1, 1), y: 262 - (value - low) * 228 / (high - low) }));
    this.linePath = this.points.map((point, index) => `${index ? 'L' : 'M'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' ');
    this.areaPath = `${this.linePath} L884 262 L58 262 Z`;
    this.firstClose = values[0]; this.latestClose = values[values.length - 1]; this.highClose = max; this.lowClose = min;
    this.periodChange = values.length > 1 && values[0] ? (this.latestClose / values[0] - 1) * 100 : 0;
  }

  private dateParam(date: Date): string { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) return '目前無法連線至研究服務，請稍後重試。';
    if (error instanceof HttpErrorResponse && error.status === 404) return '此期間沒有可用的收盤資料，請選擇其他期間。';
    if (error instanceof HttpErrorResponse && error.status === 503) return '行情展示權尚待確認，這個環境目前未開放歷史走勢資料。';
    return error instanceof HttpErrorResponse && typeof error.error?.error === 'string' ? error.error.error : '資料載入失敗，請稍後重試。';
  }
}
