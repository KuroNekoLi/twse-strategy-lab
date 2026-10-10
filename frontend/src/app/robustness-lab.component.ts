import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

interface MatrixMetric { totalReturnPercent: number; annualizedReturnPercent: number; maxDrawdownPercent: number; annualizedRealizedVolatilityPercent: number; }
interface MatrixRange { min: number; median: number; max: number; }
interface RobustnessResponse { baseId: string; symbol: string; requestedFrom: string; requestedTo: string; observedFrom: string; observedTo: string; sampleCount: number; status: string; dataSource?: string; limitations: string[]; cases: { id: string; sampleCount: number; metrics: MatrixMetric }[]; aggregate: { totalReturnPercent: MatrixRange; annualizedReturnPercent: MatrixRange; maxDrawdownPercent: MatrixRange; annualizedRealizedVolatilityPercent: MatrixRange } }
const API_BASE = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');

function apiErrorCode(value: unknown): string | undefined {
  if (typeof value !== 'object' || value === null || !('code' in value)) return undefined;
  const code = value.code;
  return typeof code === 'string' ? code : undefined;
}

function robustnessErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return '分析未能完成，請稍後重試。';
  if (error.status === 0) return '目前無法連線至回測服務。請確認網路連線與後端服務狀態，再重試。';
  switch (apiErrorCode(error.error)) {
    case 'NO_MARKET_DATA':
      return '所選標的在指定日期範圍內沒有可用行情。請調整分析期間或選擇其他標的。';
    case 'UPSTREAM_DATA_UNAVAILABLE':
    case 'UPSTREAM_FAILURE':
      return '行情來源目前無法提供資料。請稍後重試；若持續發生，請查看資料與方法說明。';
    case 'DATA_INTEGRITY_FAILED':
      return '取得的行情未通過資料完整性檢查，因此未執行分析。請稍後重試或改用其他期間。';
    case 'INVALID_INPUT':
      return '分析條件不符合格式或範圍要求。請檢查標的與日期。';
    default:
      return '分析未能完成，請稍後重試。';
  }
}

@Component({
  selector: 'app-robustness-lab', standalone: true, imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="page-wrap subpage robustness-page">
      <p class="eyebrow">M4 · ROBUSTNESS LAB</p><h1 tabindex="-1">檢查參數附近是否仍站得住。</h1>
      <p class="subpage-lede">固定同一段資料，測試 5 組雙均線與成本變化。看整體範圍與中位數，不尋找單一最高報酬組合。</p>
      <div class="challenge-disclosure"><strong>有限範圍的敏感度分析</strong><span>目前只支援雙均線參數與費率敏感度；不是樣本外驗證、統計顯著性或策略有效性的證明。資料不會在此頁保存，來源授權與行情完整性仍待確認。</span></div>
      <form class="matrix-form" (ngSubmit)="run()"><div class="matrix-form-grid"><label>標的代碼<input name="symbol" [(ngModel)]="symbol" inputmode="numeric" maxlength="6" required></label><label>開始日期<input name="from" type="date" [(ngModel)]="from" required></label><label>結束日期<input name="to" type="date" [(ngModel)]="to" required></label></div><div class="base-settings"><span>固定基準設定</span><strong>雙均線交叉 · 20 / 60 日</strong><small>起始投入 100,000 元 · 每月投入 10,000 元 · 五組鄰近參數／成本案例</small></div><button class="button-primary" type="submit" [disabled]="busy || !symbol.trim()">{{busy ? '讀取資料並比較中…' : '執行 5 組敏感度分析 →'}}</button><p *ngIf="error" class="notice-error" role="alert">{{error}}</p></form>
      <section *ngIf="result as report" class="matrix-report" aria-live="polite"><div class="matrix-report-heading"><div><p class="eyebrow">{{report.status}}</p><h2>{{report.symbol}} · 雙均線敏感度結果</h2><p>要求期間 {{report.requestedFrom}} 至 {{report.requestedTo}} · 實際觀察 {{report.observedFrom}} 至 {{report.observedTo}} · {{report.sampleCount}} 筆<span *ngIf="report.dataSource"> · 資料來源：{{report.dataSource}}</span></p></div><a routerLink="/methods">查看資料與成交方法 →</a></div><div class="range-grid"><article><span>總報酬範圍</span><strong>{{percent(report.aggregate.totalReturnPercent.min)}}–{{percent(report.aggregate.totalReturnPercent.max)}}</strong><small>中位數 {{percent(report.aggregate.totalReturnPercent.median)}}</small></article><article><span>最大回撤範圍</span><strong>{{percent(report.aggregate.maxDrawdownPercent.min)}}–{{percent(report.aggregate.maxDrawdownPercent.max)}}</strong><small>中位數 {{percent(report.aggregate.maxDrawdownPercent.median)}}</small></article><article><span>年化波動範圍</span><strong>{{percent(report.aggregate.annualizedRealizedVolatilityPercent.min)}}–{{percent(report.aggregate.annualizedRealizedVolatilityPercent.max)}}</strong><small>中位數 {{percent(report.aggregate.annualizedRealizedVolatilityPercent.median)}}</small></article></div><div class="matrix-table-wrap"><table class="matrix-table"><thead><tr><th scope="col">案例</th><th scope="col">樣本數</th><th scope="col">總報酬</th><th scope="col">年化報酬</th><th scope="col">最大回撤</th><th scope="col">年化波動</th></tr></thead><tbody><tr *ngFor="let item of report.cases"><th scope="row">{{caseName(item.id)}}</th><td>{{item.sampleCount}}</td><td>{{percent(item.metrics.totalReturnPercent)}}</td><td>{{percent(item.metrics.annualizedReturnPercent)}}</td><td>{{percent(item.metrics.maxDrawdownPercent)}}</td><td>{{percent(item.metrics.annualizedRealizedVolatilityPercent)}}</td></tr></tbody></table></div><details class="challenge-rules"><summary>資料限制與解讀方式</summary><p *ngFor="let item of report.limitations">{{item}}</p><p>各案例只沿用本次請求的同一組資料；沒有行情快照保存，因此之後重跑可能使用更新資料。敏感度範圍不能取代跨期間測試、樣本外驗證或研究者判斷。</p></details></section>
    </section>
  `,
})
export class RobustnessLabComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);
  symbol = '0050'; from = '2020-01-01'; to = new Date().toISOString().slice(0, 10);
  busy = false; error = ''; result: RobustnessResponse | null = null;
  constructor() { this.symbol = this.route.snapshot.queryParamMap.get('symbol') ?? this.symbol; this.from = this.route.snapshot.queryParamMap.get('from') ?? this.from; this.to = this.route.snapshot.queryParamMap.get('to') ?? this.to; }
  async run(): Promise<void> {
    if (!API_BASE) { this.error = '回測 API 尚未設定。'; return; }
    const baseId = `ma-20-60-${this.symbol}-${this.from}-${this.to}`;
    const body = {
      baseId,
      base: { symbol: this.symbol.trim(), symbols: [this.symbol.trim()], from: this.from, to: this.to, strategy: 'ma-crossover', fastWindow: 20, slowWindow: 60,
        rsiWindow: 14, rsiBuyThreshold: 30, rsiSellThreshold: 55, bollingerWindow: 20, bollingerMultiplier: 2, breakoutWindow: 20, drawdownBuyPercent: 20, profitSellPercent: 20,
        monthlyContribution: 10000, initialCapital: 100000, commissionRate: 0.001425, sellTaxRate: 0.003, useMarketTaxDefaults: false },
      variants: [
        { id: 'short-window-15', fastWindow: 15, slowWindow: 60, commissionRate: null, sellTaxRate: null },
        { id: 'short-window-25', fastWindow: 25, slowWindow: 60, commissionRate: null, sellTaxRate: null },
        { id: 'long-window-50', fastWindow: 20, slowWindow: 50, commissionRate: null, sellTaxRate: null },
        { id: 'long-window-70', fastWindow: 20, slowWindow: 70, commissionRate: null, sellTaxRate: null },
        { id: 'higher-cost', fastWindow: null, slowWindow: null, commissionRate: 0.00285, sellTaxRate: 0.006 },
      ],
    };
    this.busy = true; this.error = ''; this.result = null;
    try { this.result = await firstValueFrom(this.http.post<RobustnessResponse>(`${API_BASE}/api/v1/robustness-matrices`, body)); }
    catch (error: unknown) { this.error = robustnessErrorMessage(error); }
    finally { this.busy = false; this.changeDetector.markForCheck(); }
  }
  percent(value: number): string { return Number.isFinite(value) ? `${value.toFixed(2)}%` : '無法計算'; }
  caseName(id: string): string { return ({ 'short-window-15': '短均線 15 / 長均線 60', 'short-window-25': '短均線 25 / 長均線 60', 'long-window-50': '短均線 20 / 長均線 50', 'long-window-70': '短均線 20 / 長均線 70', 'higher-cost': '較高成本：手續費與交易稅加倍' } as Record<string,string>)[id] ?? id; }
}
