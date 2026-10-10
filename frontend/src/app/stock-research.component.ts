import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom } from 'rxjs';
import { isCalendarDate } from './date-utils';

type Instrument = { code: string; name: string; kind: string; asOf: string; market: string; backtestSupported: boolean };
type ChartInterval = '1d' | '1w' | '1mo';
type PeriodWindowStatus = 'ELAPSED' | 'CLIPPED_BY_REQUEST' | 'IN_PROGRESS' | 'UNKNOWN';
type PriceBar = { date: string; periodStart?: string; periodEnd?: string; observedFrom?: string; observedTo?: string; periodWindowStatus?: PeriodWindowStatus; coverageStatus?: 'UNKNOWN'; open?: number | string | null; high?: number | string | null; low?: number | string | null; close: number | string; volume?: number | string | null };
type History = { symbol: string; from: string; to: string; observedFrom: string; observedTo: string; fetchedAt: string; source: string; interval: ChartInterval; licensingStatus: string; adjustmentPolicy: string; bars: PriceBar[]; limitations: string[] };
type ChartBar = { date: string; periodStart: string; periodEnd: string | null; observedFrom: string | null; observedTo: string | null; periodWindowStatus: PeriodWindowStatus; coverageStatus: 'UNKNOWN'; open: number | null; high: number | null; low: number | null; close: number; volume: number | null; sma20: number | null; sma60: number | null; sma20Y: number | null; sma60Y: number | null; x: number; openY: number | null; highY: number | null; lowY: number | null; closeY: number; volumeY: number; volumeHeight: number; candleY: number; candleHeight: number; up: boolean };
type LiveQuote = { symbol: string; price: number; size: number | null; volume: number | null; eventTime: string; receivedAt: string; source: string; freshness: 'LIVE' };
type LiveState = 'disabled' | 'connecting' | 'waiting' | 'live' | 'stale' | 'network' | 'error' | 'unavailable' | 'disconnected';
const apiBaseUrl = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');
const liveDataOptIn = (window.APP_CONFIG as (Window['APP_CONFIG'] & { liveMarketDataEnabled?: boolean }) | undefined)?.liveMarketDataEnabled === true;
const localBrowser = ['localhost', '127.0.0.1', '::1', '[::1]'].includes(window.location.hostname);
const localLiveEnabled = liveDataOptIn && localBrowser;

@Component({
  selector: 'app-stock-research-page', standalone: true, imports: [CommonModule, RouterLink],
  styles: [`.stock-control-group{display:grid;gap:4px}.stock-control-label{font-size:11px;color:#686d65}.indicator-note{font-size:11px;color:#686d65}.period-window-status{font-weight:650}`],
  template: `
    <section class="page-wrap subpage stock-research-page">
      <a *ngIf="!instrument && !error" class="stock-back-link" routerLink="/explore">← 回到標的探索</a>
      <ng-container *ngIf="instrument as item; else loadingInstrument">
        <div class="stock-heading"><div><p class="stock-identity">{{item.code}}　{{item.market === 'TWSE' ? '臺灣證券交易所上市' : '市場未確認'}}</p><h1 tabindex="-1">{{item.name}}</h1></div><a class="stock-back-link stock-back-inline" routerLink="/explore">← 標的探索</a></div>

        <div *ngIf="error" class="stock-error" role="alert"><strong>目前無法載入行情</strong><span>{{error}}</span><button type="button" (click)="loadHistory()">重新載入</button></div>
        <p *ngIf="dateFocusMessage" class="chart-date-focus" role="status" aria-live="polite">{{dateFocusMessage}}</p>
        <div *ngIf="busy" class="stock-loading" role="status">正在取得歷史行情…</div>
        <ng-container *ngIf="history as data">
          <section class="stock-chart-card" aria-labelledby="stock-chart-title">
            <div class="stock-live-label"><span class="stock-status-dot" aria-hidden="true"></span><strong>歷史{{intervalName}}線</strong><span>最後觀察日 {{data.observedTo}}</span><span class="daily-data-label">歷史資料 · 非即時</span></div>
            <div class="stock-chart-heading">
              <div><p class="stock-price-context">收盤價 · {{selectedBar?.observedTo ?? data.observedTo}}</p><h2 id="stock-chart-title">{{(selectedBar?.close ?? latestClose) | number:'1.2-2'}} <small>元</small></h2><p class="stock-date-line">請求期間 {{data.from}} – {{data.to}} · 觀察資料 {{dateRange(data.observedFrom, data.observedTo)}}</p></div>
              <div class="stock-chart-controls">
                <div class="stock-control-group"><span class="stock-control-label">顯示範圍</span><div class="stock-periods" role="group" aria-label="顯示範圍：1 年、3 年或 5 年"><button *ngFor="let period of periods" type="button" [disabled]="busy" [class.selected]="selectedPeriod === period" [attr.aria-pressed]="selectedPeriod === period" (click)="changePeriod(period)">{{period}}</button></div></div>
                <div class="stock-control-group"><span class="stock-control-label">K 線週期</span><div class="stock-periods stock-intervals" role="group" aria-label="K 線週期：日、週或月"><button *ngFor="let interval of intervals" type="button" [disabled]="busy" [class.selected]="selectedInterval === interval.value" [attr.aria-pressed]="selectedInterval === interval.value" (click)="changeInterval(interval.value)">{{interval.label}}</button></div></div>
                <div class="stock-periods stock-modes" role="group" aria-label="圖表類型"><button type="button" [class.selected]="chartMode === 'line'" [attr.aria-pressed]="chartMode === 'line'" (click)="setMode('line')">走勢</button><button type="button" [disabled]="!hasOhlc" [class.selected]="chartMode === 'candles'" [attr.aria-pressed]="chartMode === 'candles'" [attr.title]="hasOhlc ? '顯示 OHLC K 線' : '此資料來源尚未提供開高低收資料'" (click)="setMode('candles')">K 線</button></div>
                <div class="stock-periods stock-indicators" role="group" aria-label="簡單移動平均線"><button type="button" [class.selected]="showSma20" [attr.aria-pressed]="showSma20" [attr.aria-label]="'切換 20 ' + intervalUnit + '簡單移動平均線'" (click)="showSma20 = !showSma20">MA 20{{intervalUnit}}</button><button type="button" [class.selected]="showSma60" [attr.aria-pressed]="showSma60" [attr.aria-label]="'切換 60 ' + intervalUnit + '簡單移動平均線'" (click)="showSma60 = !showSma60">MA 60{{intervalUnit}}</button></div>
                <div class="stock-periods stock-indicators" role="group" aria-label="成交量圖層"><button type="button" [disabled]="!hasVolume" [class.selected]="showVolume" [attr.aria-pressed]="showVolume" [attr.title]="hasVolume ? '切換成交量圖層' : '此資料來源尚未提供成交量'" (click)="showVolume = !showVolume">成交量</button></div>
                <span class="indicator-note" *ngIf="showSma20 || showSma60">以收盤價計算簡單移動平均；不足 {{intervalUnit}}數時不繪製。</span>
                <span class="indicator-note" *ngIf="!hasOhlc">此資料來源未提供完整開、高、低、收欄位，K 線暫不可用。</span>
                <span class="indicator-note" *ngIf="!hasVolume">此資料來源未提供成交量，成交量圖層暫不可用。</span>
              </div>
            </div>
            <div class="stock-chart-summary"><span [class.negative]="periodChange < 0" [class.positive]="periodChange > 0">{{periodChange >= 0 ? '+' : ''}}{{periodChange | number:'1.2-2'}}%</span><span>所選顯示範圍報酬</span><span class="chart-summary-divider"></span><span>{{chartBars.length}} 根{{intervalName}} K</span><span *ngIf="showVolume && hasVolume" class="chart-legend"><i class="legend-up"></i>上漲 <i class="legend-down"></i>下跌</span></div>
            <div class="stock-selected-quote" aria-live="polite" aria-atomic="true" *ngIf="selectedBar as quote">
              <strong>{{dateRange(quote.periodStart, quote.periodEnd)}}</strong>
              <span [class.positive]="selectedDailyChange !== null && selectedDailyChange > 0" [class.negative]="selectedDailyChange !== null && selectedDailyChange < 0">
                <ng-container *ngIf="selectedDailyChange !== null; else noDailyChange">較前一{{intervalName}} {{selectedDailyChange > 0 ? '+' : ''}}{{selectedDailyChange | number:'1.2-2'}}%（{{selectedDailyPoints! > 0 ? '+' : ''}}{{selectedDailyPoints | number:'1.2-2'}} 元）</ng-container>
                <ng-template #noDailyChange>無前一{{intervalName}}資料</ng-template>
              </span>
              <ng-container *ngIf="hasOhlc"><span>開 {{quote.open | number:'1.2-2'}}</span><span>高 {{quote.high | number:'1.2-2'}}</span><span>低 {{quote.low | number:'1.2-2'}}</span></ng-container>
              <span>收 {{quote.close | number:'1.2-2'}}</span>
              <span *ngIf="quote.volume !== null">量 {{quote.volume | number}}</span>
              <span *ngIf="showSma20 && quote.sma20 !== null" class="sma-value sma20-value">MA20{{intervalUnit}} {{quote.sma20 | number:'1.2-2'}}</span>
              <span *ngIf="showSma60 && quote.sma60 !== null" class="sma-value sma60-value">MA60{{intervalUnit}} {{quote.sma60 | number:'1.2-2'}}</span>
              <span *ngIf="showSma20 && quote.sma20 === null" class="sma-unavailable">MA20{{intervalUnit}} 尚無足夠資料</span>
              <span *ngIf="showSma60 && quote.sma60 === null" class="sma-unavailable">MA60{{intervalUnit}} 尚無足夠資料</span>
              <span>觀察 {{dateRange(quote.observedFrom, quote.observedTo)}}</span>
              <span class="period-window-status">{{periodWindowLabel(quote.periodWindowStatus)}} · 資料覆蓋未知</span>
            </div>
            <div class="stock-chart-frame" *ngIf="chartBars.length > 1; else noPoints">
              <svg class="stock-price-svg" viewBox="0 0 940 350" role="img" [attr.aria-label]="chartDescription(item.name, data)">
                <g class="chart-grid"><line x1="64" y1="22" x2="920" y2="22"/><line x1="64" y1="89" x2="920" y2="89"/><line x1="64" y1="156" x2="920" y2="156"/><line x1="64" y1="224" x2="920" y2="224"/></g>
                <g class="chart-axis-labels"><text x="3" y="27">{{priceTicks[0] | number:'1.0-0'}}</text><text x="3" y="94">{{priceTicks[1] | number:'1.0-0'}}</text><text x="3" y="161">{{priceTicks[2] | number:'1.0-0'}}</text><text x="3" y="229">{{priceTicks[3] | number:'1.0-0'}}</text><text x="64" y="344">{{chartBars[0].periodStart}}</text><text x="920" y="344" text-anchor="end">{{chartBars[chartBars.length - 1].periodEnd}}</text><text *ngIf="showVolume && hasVolume" x="64" y="244" class="volume-axis-title">成交量</text></g>
                <path *ngIf="chartMode === 'line'" class="chart-area" [class.chart-area-up]="periodChange > 0" [class.chart-area-down]="periodChange < 0" [attr.d]="areaPath"/><path *ngIf="chartMode === 'line'" class="chart-line" [class.chart-line-up]="periodChange > 0" [class.chart-line-down]="periodChange < 0" [attr.d]="linePath"/>
                <g *ngFor="let bar of chartBars; let i = index" class="chart-mark" [class.chart-mark-active]="activeIndex === i" (mouseenter)="selectBar(i)" (click)="selectBar(i)">
                  <rect class="chart-hit-area" [attr.x]="Math.max(plot.left, bar.x - hitWidth / 2)" y="22" [attr.width]="hitWidth" height="202"/>
                  <line *ngIf="chartMode === 'candles' && hasOhlc" class="candle-wick" [class.up]="bar.up" [class.down]="!bar.up" [attr.x1]="bar.x" [attr.x2]="bar.x" [attr.y1]="bar.highY" [attr.y2]="bar.lowY"/>
                  <rect *ngIf="chartMode === 'candles' && hasOhlc" class="candle-body" [class.up]="bar.up" [class.down]="!bar.up" [attr.x]="bar.x - candleWidth / 2" [attr.y]="bar.candleY" [attr.width]="candleWidth" [attr.height]="bar.candleHeight" rx=".5"/>
                  <circle *ngIf="chartMode === 'line'" class="line-focus-point" [class.active]="activeIndex === i" [attr.cx]="bar.x" [attr.cy]="bar.closeY" [attr.r]="activeIndex === i ? 4 : 1.7"/>
                  <rect *ngIf="showVolume && hasVolume && bar.volume !== null" class="volume-bar" [class.up]="bar.up" [class.down]="!bar.up" [attr.x]="bar.x - candleWidth / 2" [attr.y]="bar.volumeY" [attr.width]="candleWidth" [attr.height]="bar.volumeHeight"/>
                </g>
                <path *ngIf="showSma20" class="moving-average-line moving-average-20" [attr.d]="sma20Path"/><path *ngIf="showSma60" class="moving-average-line moving-average-60" [attr.d]="sma60Path"/>
                <line *ngIf="activeBar" class="chart-crosshair" [attr.x1]="activeBar.x" [attr.x2]="activeBar.x" y1="18" y2="224"/>
                <ng-container *ngIf="liveState === 'live' && liveQuote && liveQuote.price >= priceScaleLow && liveQuote.price <= priceScaleHigh"><line class="live-price-line" x1="64" x2="920" [attr.y1]="liveQuoteY" [attr.y2]="liveQuoteY"/><text class="live-price-tag" x="914" [attr.y]="liveQuoteY - 4" text-anchor="end">LIVE {{liveQuote.price | number:'1.2-2'}}</text></ng-container>
              </svg>
              <div *ngIf="showVolume && hasVolume" class="volume-caption"><span>所選{{intervalName}} K 成交量</span><span>{{selectedBar?.volume === null || selectedBar?.volume === undefined ? '此筆無資料' : (selectedBar.volume | number)}}</span></div>
              <label class="sr-only" for="chart-point-selector">選擇圖表{{intervalName}} K</label><input id="chart-point-selector" class="chart-point-selector" type="range" min="0" [max]="chartBars.length - 1" [value]="activeIndex" (input)="selectBar(+$any($event.target).value)" [attr.aria-valuetext]="selectedBar ? sliderLabel(selectedBar) : ''"/>
              <p class="sr-only">{{chartDescription(item.name, data)}}。圖表可使用下方滑桿逐{{intervalName}}檢視。</p>
            </div>
            <ng-template #noPoints><div class="stock-no-points">此期間沒有足夠的有效行情資料。請更換期間或稍後重試。</div></ng-template>
            <section class="live-quote-panel" [class.live-quote-active]="liveState === 'live'" [class.live-quote-warning]="liveState === 'stale' || liveState === 'network' || liveState === 'error'" aria-label="即時行情狀態" aria-live="polite" aria-atomic="true">
              <div class="live-quote-heading"><strong>即時行情</strong><span class="live-state-pill" [class.live-state-on]="liveState === 'live'">{{liveStateLabel}}</span></div>
              <ng-container *ngIf="liveQuote as quote; else noLiveQuote"><div class="live-quote-value"><strong>{{quote.price | number:'1.2-2'}} <small>元</small></strong><span>{{quote.symbol}} · {{liveState === 'live' ? 'LIVE' : '最近報價'}}</span></div><div class="live-quote-meta"><span>事件時間 {{formatTime(quote.eventTime)}}</span><span>收到時間 {{formatTime(quote.receivedAt)}}</span><span>來源 {{quote.source || '未提供'}}</span><span *ngIf="quote.size !== null">單筆量 {{quote.size | number}}</span><span *ngIf="quote.volume !== null">累計量 {{quote.volume | number}}</span></div><p class="live-quote-note">即時報價獨立呈現；下方圖表仍是日線歷史行情，不會將報價併入日 K。</p></ng-container>
              <ng-template #noLiveQuote><p class="live-quote-message">{{liveStateMessage}}</p></ng-template>
            </section>
            <div class="stock-data-foot"><span>來源：{{data.source}}</span><span>擷取時間：{{data.fetchedAt}}</span><span>價格調整：{{data.adjustmentPolicy || '未提供'}}</span><span class="data-status">{{data.licensingStatus === 'CONFIRMED' ? '展示授權已確認' : '資料展示授權尚未確認'}}</span></div>
          </section>
          <section class="stock-next-step"><div><p class="eyebrow">同一標的，接續研究</p><h2>從觀察走向檢驗與紀錄</h2><p>把 {{item.code}} 帶到回測或研究筆記；資料仍是歷史資料。</p></div><nav class="stock-context-nav" aria-label="個股研究導覽"><a href="#stock-chart-title" aria-current="location">走勢</a><a class="button-primary" [routerLink]="'/backtest'" [queryParams]="{symbol:item.code}">回測 {{item.code}} →</a><a class="button-outline" [routerLink]="'/journal'" [queryParams]="{symbol:item.code}">寫研究筆記 →</a><a class="button-quiet" routerLink="/watchlist">觀察清單</a></nav></section>
          <section class="stock-data-notice"><div class="notice-mark" aria-hidden="true">i</div><div><strong>資料範圍與限制</strong><p>圖表呈現 {{hasOhlc ? '開、高、低、收' : '收盤'}}歷史行情{{hasVolume ? '與成交量' : ''}}，屬歷史資料，不代表即時報價或可交易價格。股利、公司行動及交易日曆完整性可能影響比較；來源限制：{{data.limitations.join('、') || '無其他說明'}}。資料授權狀態：{{data.licensingStatus || '未知'}}。</p><a href="https://openapi.twse.com.tw/" target="_blank" rel="noopener noreferrer">查看 TWSE OpenAPI ↗</a></div></section>
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
  private readonly destroyRef = inject(DestroyRef);
  readonly periods = ['1年', '3年', '5年'];
  readonly intervals: { value: ChartInterval; label: string }[] = [{ value: '1d', label: '日 K' }, { value: '1w', label: '週 K' }, { value: '1mo', label: '月 K' }];
  symbol = '';
  instrument: Instrument | null = null;
  history: History | null = null;
  selectedPeriod = '1年';
  selectedInterval: ChartInterval = '1d';
  chartMode: 'line' | 'candles' = 'line';
  showSma20 = false;
  showSma60 = false;
  showVolume = true;
  busy = false;
  error = '';
  chartBars: ChartBar[] = [];
  priceTicks: number[] = [0, 0, 0, 0];
  linePath = '';
  areaPath = '';
  latestClose = 0;
  firstClose = 0;
  periodChange = 0;
  activeIndex = 0;
  requestedFocusDate = '';
  dateFocusMessage = '';
  liveState: LiveState = localLiveEnabled ? 'connecting' : 'disabled';
  liveQuote: LiveQuote | null = null;
  private liveStream: EventSource | null = null;
  private liveStreamSymbol = '';
  private staleTimer: number | null = null;
  private lastLiveReceiptMs = 0;
  private hasServerStreamEvidence = false;
  private streamErrorReceived = false;
  private historyRequestId = 0;
  private instrumentRequestId = 0;
  private readonly plot = { left: 64, right: 920, top: 22, bottom: 224 };
  priceScaleLow = 0;
  priceScaleHigh = 1;
  readonly Math = Math;

  /** The chart can remain visible while another interval is loading; label it from the data currently rendered. */
  get displayedInterval(): ChartInterval { return this.history?.interval ?? this.selectedInterval; }
  get intervalName(): string { return this.displayedInterval === '1d' ? '日' : this.displayedInterval === '1w' ? '週' : '月'; }
  get intervalUnit(): string { return this.intervalName; }

  constructor() {
    this.destroyRef.onDestroy(() => this.closeLiveStream());
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.closeLiveStream();
      this.symbol = params.get('symbol') ?? '';
      this.instrument = null; this.history = null; this.chartBars = []; this.busy = false; this.error = '';
      this.liveQuote = null; this.liveState = localLiveEnabled ? 'connecting' : 'disabled';
      this.historyRequestId += 1;
      const requestId = ++this.instrumentRequestId;
      if (!/^\d{4,6}$/.test(this.symbol)) { this.error = '標的代碼格式不正確。'; this.cdr.markForCheck(); return; }
      void this.loadInstrument(this.symbol, requestId);
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const requested = params.get('date') ?? '';
      this.requestedFocusDate = isCalendarDate(requested) ? requested : '';
      this.dateFocusMessage = requested && !this.requestedFocusDate ? '連結中的成交日期格式不正確，請從回測成交明細重新開啟。' : '';
      if (this.history && this.requestedFocusDate) this.focusRequestedDate();
      this.cdr.markForCheck();
    });
  }

  get hasOhlc(): boolean { return !!this.history?.bars?.length && this.history.bars.every((bar) => this.number(bar.open) !== null && this.number(bar.high) !== null && this.number(bar.low) !== null); }
  get hasVolume(): boolean { return !!this.history?.bars?.some((bar) => this.number(bar.volume) !== null); }
  get candleWidth(): number { return Math.max(1.2, Math.min(8, (this.plot.right - this.plot.left) / Math.max(this.chartBars.length, 1) * .62)); }
  get hitWidth(): number { return Math.max(2, Math.min(18, (this.plot.right - this.plot.left) / Math.max(this.chartBars.length - 1, 1))); }
  get activeBar(): ChartBar | null { return this.chartBars[this.activeIndex] ?? null; }
  get selectedBar(): ChartBar | null { return this.activeBar; }
  get selectedDailyPoints(): number | null { const current = this.selectedBar; const previous = this.chartBars[this.activeIndex - 1]; return current && previous ? current.close - previous.close : null; }
  get selectedDailyChange(): number | null { const previous = this.chartBars[this.activeIndex - 1]; return previous?.close ? (this.selectedBar!.close / previous.close - 1) * 100 : null; }
  get sma20Path(): string { return this.movingAveragePath('sma20Y'); }
  get sma60Path(): string { return this.movingAveragePath('sma60Y'); }
  get liveQuoteY(): number { return this.plot.bottom - (this.liveQuote!.price - this.priceScaleLow) * (this.plot.bottom - this.plot.top) / (this.priceScaleHigh - this.priceScaleLow); }
  get liveStateLabel(): string {
    return ({ disabled: '未啟用', connecting: '連線中', waiting: '等待報價', live: 'LIVE · 即時', stale: '資料逾時', network: '網路重連中', error: '串流錯誤', unavailable: '目前不可用', disconnected: '已中斷' } satisfies Record<LiveState, string>)[this.liveState];
  }
  get liveStateMessage(): string {
    return ({ disabled: '本頁目前只顯示日線歷史資料。即時串流僅供本機開發設定啟用，公開頁面不會連線。', connecting: '正在建立本機即時行情連線…', waiting: '串流已連線，等待資料來源提供即時報價。', live: '等待下一筆即時報價。', stale: '超過 60 秒未收到新報價；目前數值已標示為過期。', network: '即時行情網路連線中斷，瀏覽器正在嘗試重新連線。', error: '即時行情服務回報錯誤；日線歷史資料仍可使用。', unavailable: '即時行情目前不可用；日線歷史資料仍可使用。', disconnected: '即時行情連線已中斷；日線歷史資料仍可使用。' } satisfies Record<LiveState, string>)[this.liveState];
  }

  changePeriod(period: string): void { this.selectedPeriod = period; void this.loadHistory(); }
  changeInterval(interval: ChartInterval): void { if (this.selectedInterval !== interval) { this.selectedInterval = interval; void this.loadHistory(); } }
  setMode(mode: 'line' | 'candles'): void { if (mode === 'line' || this.hasOhlc) this.chartMode = mode; }
  selectBar(index: number): void { this.activeIndex = Math.max(0, Math.min(this.chartBars.length - 1, index)); }
  periodWindowLabel(status: PeriodWindowStatus): string {
    return ({ ELAPSED: '已結束區間', CLIPPED_BY_REQUEST: '區間受查詢日期截短', IN_PROGRESS: '進行中區間', UNKNOWN: '區間狀態未知' } satisfies Record<PeriodWindowStatus, string>)[status];
  }
  dateRange(start: string | null | undefined, end: string | null | undefined): string { return `${start || '未知'} – ${end || '未知'}`; }
  sliderLabel(bar: ChartBar): string {
    const prices = this.hasOhlc ? `，開 ${bar.open}，高 ${bar.high}，低 ${bar.low}，收 ${bar.close}` : `，收 ${bar.close}`;
    return `${this.dateRange(bar.periodStart, bar.periodEnd)}，觀察 ${this.dateRange(bar.observedFrom, bar.observedTo)}${prices}，${this.periodWindowLabel(bar.periodWindowStatus)}，歷史非即時資料`;
  }

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
      const data = await firstValueFrom(this.http.get<History>(`${apiBaseUrl}/api/v1/stocks/${this.symbol}/history`, { params: { from: this.dateParam(from), to: this.dateParam(to), interval: this.selectedInterval } }));
      if (requestId === this.historyRequestId) { this.history = data; this.prepareChart(data.bars ?? []); this.connectLiveStream(); this.focusRequestedDate(); }
    } catch (failure) { if (requestId === this.historyRequestId) { this.error = this.messageFor(failure); this.history = null; this.chartBars = []; if (this.requestedFocusDate) this.dateFocusMessage = `目前無法取得足以定位 ${this.requestedFocusDate} 的歷史 K 線。請確認資料來源或稍後重試。`; } }
    finally { if (requestId === this.historyRequestId) { this.busy = false; this.cdr.markForCheck(); } }
  }

  chartDescription(name: string, data: History): string {
    return `${name}${this.chartMode === 'candles' ? `${this.intervalName} K 線` : `${this.intervalName}收盤走勢`}${this.showVolume && this.hasVolume ? '與成交量' : ''}，${data.observedFrom} 至 ${data.observedTo}，歷史資料，非即時報價；資料覆蓋未知`;
  }

  formatTime(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-TW', { dateStyle: 'medium', timeStyle: 'medium', timeZone: 'Asia/Taipei' }).format(date);
  }

  private connectLiveStream(): void {
    if (!localLiveEnabled) { this.liveState = 'disabled'; return; }
    if (this.liveStream && this.liveStreamSymbol === this.symbol) return;
    if (typeof EventSource === 'undefined') { this.liveState = 'unavailable'; return; }
    this.closeLiveStream();
    this.liveState = 'connecting';
    this.liveStreamSymbol = this.symbol;
    this.hasServerStreamEvidence = false;
    this.streamErrorReceived = false;
    const stream = new EventSource(`${apiBaseUrl}/api/v1/stocks/${encodeURIComponent(this.symbol)}/live`);
    this.liveStream = stream;
    stream.onopen = () => { if (this.liveStream === stream) { this.liveState = 'waiting'; this.cdr.markForCheck(); } };
    stream.onerror = () => {
      if (this.liveStream !== stream) return;
      if (this.streamErrorReceived) return;
      this.liveState = stream.readyState === EventSource.CLOSED
        ? this.hasServerStreamEvidence ? 'disconnected' : 'unavailable'
        : 'network';
      this.cdr.markForCheck();
    };
    stream.addEventListener('status', (event) => this.onLiveStatus(stream, (event as MessageEvent<string>).data));
    stream.addEventListener('quote', (event) => this.onLiveQuote(stream, (event as MessageEvent<string>).data));
    stream.addEventListener('stream-error', (event) => this.onLiveError(stream, (event as MessageEvent<string>).data));
    this.staleTimer = window.setInterval(() => {
      if (this.liveQuote && this.liveState === 'live' && Date.now() - this.lastLiveReceiptMs > 60_000) {
        this.liveState = 'stale'; this.cdr.markForCheck();
      }
    }, 5_000);
    this.cdr.markForCheck();
  }

  private onLiveStatus(stream: EventSource, raw: string): void {
    if (this.liveStream !== stream) return;
    this.hasServerStreamEvidence = true;
    try {
      const payload = JSON.parse(raw) as { status?: string; source?: string; freshness?: string; message?: string };
      const status = (payload.status ?? '').toUpperCase().replaceAll('-', '_');
      if (status.includes('UNAVAILABLE') || status.includes('DISABLED')) this.liveState = 'unavailable';
      else if (status.includes('CONNECTING') || status.includes('AUTHENTICATING')) this.liveState = 'connecting';
      else if (status.includes('STALE')) this.liveState = 'stale';
      else if (status.includes('DISCONNECT') || status.includes('CLOSED')) this.liveState = 'disconnected';
      else if (status.includes('NETWORK') || status.includes('RECONNECT')) this.liveState = 'network';
      else if (status.includes('ERROR') || status.includes('FAIL')) this.liveState = 'error';
      else if (status.includes('LIVE')) this.liveState = this.liveQuote ? 'live' : 'waiting';
      else this.liveState = 'waiting';
    } catch { this.liveState = 'error'; }
    this.cdr.markForCheck();
  }

  private onLiveQuote(stream: EventSource, raw: string): void {
    if (this.liveStream !== stream) return;
    this.hasServerStreamEvidence = true;
    try {
      const payload = JSON.parse(raw) as Partial<LiveQuote>;
      const price = Number(payload.price);
      if (payload.symbol !== this.symbol || !Number.isFinite(price) || !payload.eventTime || !payload.receivedAt || payload.freshness !== 'LIVE') return;
      this.liveQuote = {
        symbol: payload.symbol, price,
        size: payload.size === undefined || payload.size === null ? null : Number(payload.size),
        volume: payload.volume === undefined || payload.volume === null ? null : Number(payload.volume),
        eventTime: payload.eventTime, receivedAt: payload.receivedAt, source: payload.source ?? '未提供', freshness: 'LIVE',
      };
      this.lastLiveReceiptMs = Date.now(); this.liveState = 'live';
    } catch { this.liveState = 'error'; }
    this.cdr.markForCheck();
  }

  private onLiveError(stream: EventSource, raw: string): void {
    if (this.liveStream !== stream) return;
    this.streamErrorReceived = true;
    try {
      const payload = JSON.parse(raw) as { code?: string; status?: string };
      const code = (payload.code ?? payload.status ?? '').toUpperCase();
      this.liveState = code === 'LIVE_PROVIDER_UNAVAILABLE' ? 'unavailable'
        : code === 'LIVE_PROVIDER_DISCONNECTED' ? 'disconnected'
          : code.startsWith('LIVE_PROVIDER_') ? 'error'
            : code.includes('UNAVAILABLE') ? 'unavailable'
              : code.includes('DISCONNECT') ? 'disconnected'
                : code.includes('NETWORK') ? 'network' : 'error';
    } catch { this.liveState = 'error'; }
    this.cdr.markForCheck();
  }

  private closeLiveStream(): void {
    this.liveStream?.close(); this.liveStream = null; this.liveStreamSymbol = '';
    if (this.staleTimer !== null) { window.clearInterval(this.staleTimer); this.staleTimer = null; }
    this.liveQuote = null; this.lastLiveReceiptMs = 0;
  }
  private prepareChart(bars: PriceBar[]): void {
    const valid = bars.map((bar) => {
      const daily = this.displayedInterval === '1d';
      const anchor = bar.periodStart ?? bar.date;
      return {
        date: bar.date, periodStart: anchor, periodEnd: bar.periodEnd ?? (daily ? bar.date : null),
        observedFrom: bar.observedFrom ?? (daily ? bar.date : null), observedTo: bar.observedTo ?? (daily ? bar.date : null),
        periodWindowStatus: bar.periodWindowStatus ?? (daily ? 'ELAPSED' as const : 'UNKNOWN' as const), coverageStatus: 'UNKNOWN' as const,
        open: this.number(bar.open), high: this.number(bar.high), low: this.number(bar.low), close: this.number(bar.close), volume: this.number(bar.volume),
      };
    }).filter((bar) => !!bar.date && bar.close !== null);
    if (!valid.length) { this.chartBars = []; return; }
    const completeOhlc = valid.every((bar) => bar.open !== null && bar.high !== null && bar.low !== null);
    const min = Math.min(...valid.map((bar) => completeOhlc ? bar.low! : bar.close!)), max = Math.max(...valid.map((bar) => completeOhlc ? bar.high! : bar.close!));
    const spread = max - min || Math.max(Math.abs(max) * .02, 1), low = min - spread * .08, high = max + spread * .08;
    this.priceScaleLow = low; this.priceScaleHigh = high;
    this.priceTicks = [high, high - (high - low) / 3, high - (high - low) * 2 / 3, low];
    const xSpan = this.plot.right - this.plot.left, ySpan = this.plot.bottom - this.plot.top;
    const maxVolume = Math.max(...valid.map((bar) => bar.volume ?? 0), 1);
    this.chartBars = valid.map((bar, index) => {
      const close = bar.close!;
      const x = this.plot.left + index * xSpan / Math.max(valid.length - 1, 1);
      const y = (value: number) => this.plot.bottom - (value - low) * ySpan / (high - low);
      const openY = bar.open === null ? null : y(bar.open), closeY = y(close);
      const volumeHeight = bar.volume === null ? 0 : bar.volume / maxVolume * 58;
      const average = (window: number): number | null => index < window - 1 ? null : valid.slice(index - window + 1, index + 1).reduce((sum, point) => sum + point.close!, 0) / window;
      const sma20 = average(20), sma60 = average(60);
      const highY = completeOhlc ? y(bar.high!) : null, lowY = completeOhlc ? y(bar.low!) : null;
      return { ...bar, close, sma20, sma60, sma20Y: sma20 === null ? null : y(sma20), sma60Y: sma60 === null ? null : y(sma60), x, openY, closeY, highY, lowY, volumeY: 318 - volumeHeight, volumeHeight, candleY: openY === null ? closeY : Math.min(openY, closeY), candleHeight: openY === null ? 0 : Math.max(Math.abs(openY - closeY), 1.2), up: bar.open === null ? true : close >= bar.open };
    });
    const closeValues = this.chartBars.map((bar) => bar.close);
    this.linePath = this.chartBars.map((bar, index) => `${index ? 'L' : 'M'}${bar.x.toFixed(1)} ${bar.closeY.toFixed(1)}`).join(' ');
    this.areaPath = `${this.linePath} L${this.plot.right} ${this.plot.bottom} L${this.plot.left} ${this.plot.bottom} Z`;
    this.firstClose = closeValues[0]; this.latestClose = closeValues[closeValues.length - 1];
    this.periodChange = closeValues.length > 1 && this.firstClose ? (this.latestClose / this.firstClose - 1) * 100 : 0;
    this.activeIndex = this.chartBars.length - 1;
    if (!completeOhlc) this.chartMode = 'line';
  }

  private movingAveragePath(field: 'sma20Y' | 'sma60Y'): string {
    return this.chartBars.flatMap((bar, index) => bar[field] === null ? [] : [`${index && this.chartBars[index - 1][field] !== null ? 'L' : 'M'}${bar.x.toFixed(1)} ${bar[field]!.toFixed(1)}`]).join(' ');
  }

  private focusRequestedDate(): void {
    if (!this.requestedFocusDate) return;
    if (!this.chartBars.length) {
      this.dateFocusMessage = `目前沒有可供比對的歷史 K 線，無法定位 ${this.requestedFocusDate}。`;
      return;
    }
    const match = this.chartBars.findIndex((bar) => bar.date === this.requestedFocusDate);
    if (match >= 0) {
      this.selectBar(match);
      this.dateFocusMessage = `已選取成交日 ${this.requestedFocusDate}；圖表只呈現該日期的可用歷史 K 線。`;
      return;
    }
    const earliest = this.chartBars[0].date;
    if (this.requestedFocusDate < earliest && this.selectedPeriod !== '5年') {
      this.selectedPeriod = '5年';
      void this.loadHistory();
      return;
    }
    this.dateFocusMessage = `目前圖表資料沒有 ${this.requestedFocusDate} 的 K 線，未自動改選其他日期。可查看資料期間或返回回測成交明細。`;
  }

  private number(value: unknown): number | null { if (value === null || value === undefined || value === '') return null; const parsed = Number(value); return Number.isFinite(parsed) ? parsed : null; }
  private dateParam(date: Date): string { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 0) return '目前無法連線至研究服務，請稍後重試。';
    if (error instanceof HttpErrorResponse && error.status === 404) return '此期間沒有可用的行情資料，請選擇其他期間。';
    if (error instanceof HttpErrorResponse && error.status === 503) return '行情展示權尚待確認，這個環境目前未開放歷史資料。';
    return error instanceof HttpErrorResponse && typeof error.error?.error === 'string' ? error.error.error : '資料載入失敗，請稍後重試。';
  }
}
