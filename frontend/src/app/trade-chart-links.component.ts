import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { isCalendarDate } from './date-utils';

@Component({
  selector: 'app-trade-chart-links',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <p *ngIf="trades.length && symbols.length > 1" class="trade-chart-limit">此回測包含多個標的，成交資料未提供逐筆標的代碼；為避免錯誤導向，目前不連結到個股圖表。</p>
    <div *ngIf="trades.length" class="trade-chart-list">
      <article *ngFor="let trade of sample(trades)" class="trade-chart-row">
        <div><strong>{{ trade['side'] === 'BUY' ? '買進' : trade['side'] === 'SELL' ? '賣出' : trade['side'] || '方向未提供' }}</strong><span>訊號日 {{ calendarDate(trade['signalDate']) ? trade['signalDate'] : '未提供' }} · 成交日 {{ calendarDate(trade['executionDate']) ? trade['executionDate'] : '未提供' }}</span></div>
        <p>{{ trade['reason'] || '未提供交易原因' }} · {{ trade['quantity'] ?? '—' }} 股 · {{ trade['price'] ?? '—' }} 元</p>
        <a *ngIf="symbols.length === 1 && calendarDate(trade['executionDate'])" [routerLink]="['/stocks', symbols[0]]" [queryParams]="{date: trade['executionDate']}">查看 {{ symbols[0] }} 當日圖表 →</a>
      </article>
      <p *ngIf="trades.length > 6" class="trade-chart-limit">此處節錄前 3 筆與後 3 筆；完整成交明細仍以本次回測回應為準。</p>
    </div>
  `,
})
export class TradeChartLinksComponent {
  @Input({ required: true }) trades: Record<string, unknown>[] = [];
  @Input({ required: true }) symbols: string[] = [];

  sample(rows: Record<string, unknown>[]): Record<string, unknown>[] {
    return rows.length <= 6 ? rows : [...rows.slice(0, 3), ...rows.slice(-3)];
  }

  calendarDate(value: unknown): value is string {
    return typeof value === 'string' && isCalendarDate(value);
  }
}
