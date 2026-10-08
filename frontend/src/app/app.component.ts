import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

type StrategyId = 'ma-crossover' | 'rsi-reversion' | 'bollinger-reversion' | 'breakout' | 'drawdown-entry';
type Point = { date: string; value: number };
type StrategyResult = {
  key: string;
  name: string;
  endingValue: number;
  contributed: number;
  totalReturn: number;
  annualizedReturn: number;
  maxDrawdown: number;
  series: Point[];
};
type BacktestResponse = {
  symbol: string;
  symbols: string[];
  from: string;
  to: string;
  tradingDays: number;
  dataSource: string;
  assumptions: string[];
  results: StrategyResult[];
};
type StrategyOption = { id: StrategyId; name: string; summary: string; category: string };

const colors = ['#21c7a8', '#b3c2d4', '#f0a84b', '#6f8fe8', '#d780a8', '#73a86c'];

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
})
export class AppComponent {
  private readonly http = inject(HttpClient);
  readonly apiBaseUrl = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');
  readonly yearOptions = Array.from({ length: new Date().getFullYear() - 2010 + 1 }, (_, index) => String(2010 + index));
  readonly strategyOptions: StrategyOption[] = [
    { id: 'ma-crossover', name: '雙均線交叉', summary: '短均線高於長均線持有；反向時退場', category: '趨勢' },
    { id: 'rsi-reversion', name: 'RSI 均值回歸', summary: 'RSI 低於買進門檻進場，高於賣出門檻出場', category: '均值回歸' },
    { id: 'bollinger-reversion', name: '布林通道回歸', summary: '收盤跌破下軌買進，回到中線賣出', category: '均值回歸' },
    { id: 'breakout', name: '區間突破', summary: '突破前 N 日高點買進，跌回均線下方賣出', category: '突破' },
    { id: 'drawdown-entry', name: '自訂回跌 / 獲利', summary: '距近一年高點回跌達門檻買進，達目標報酬賣出', category: '自訂規則' },
  ];

  symbols = ['0050'];
  symbolInput = '';
  strategy: StrategyId = 'ma-crossover';
  fromYear = '2010';
  toYear = String(new Date().getFullYear());
  fast = '20';
  slow = '60';
  rsiWindow = '14';
  rsiBuy = '30';
  rsiSell = '55';
  bollingerWindow = '20';
  bollingerMultiplier = '2';
  breakoutWindow = '20';
  drawdownBuy = '20';
  profitSell = '20';
  monthly = '10000';
  initial = '100000';
  commission = '0.1425';
  tax = '';
  response: BacktestResponse | null = null;
  busy = false;
  error = '';

  get selectedStrategy(): StrategyOption {
    return this.strategyOptions.find((item) => item.id === this.strategy) ?? this.strategyOptions[0];
  }

  get leadingResult(): StrategyResult | undefined {
    return this.response?.results.slice().sort((a, b) => b.endingValue - a.endingValue)[0];
  }

  get chartTicks(): number[] {
    const values = this.response?.results.flatMap((result) => result.series.map((point) => point.value)) ?? [];
    if (!values.length) return [];
    const min = Math.min(...values) * 0.94;
    const max = Math.max(...values) * 1.04;
    return [min, (min + max) / 2, max];
  }

  get chartBounds(): { min: number; max: number } {
    const values = this.response?.results.flatMap((result) => result.series.map((point) => point.value)) ?? [];
    if (!values.length) return { min: 0, max: 1 };
    return { min: Math.min(...values) * 0.94, max: Math.max(...values) * 1.04 };
  }

  get chartFirstDate(): string {
    return this.response?.results[0]?.series[0]?.date ?? '';
  }

  get chartLastDate(): string {
    const series = this.response?.results[0]?.series ?? [];
    return series[series.length - 1]?.date ?? '';
  }

  isInvalid(): boolean {
    return Number(this.fromYear) > Number(this.toYear)
      || (this.strategy === 'ma-crossover' && Number(this.fast) >= Number(this.slow));
  }

  formatMoney(value: number): string {
    return new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 }).format(value);
  }

  formatPercent(value: number): string {
    return `${value >= 0 ? '+' : '−'}${Math.abs(value).toFixed(1)}%`;
  }

  tickY(value: number): number {
    return this.valueY(value) + 4;
  }

  valueY(value: number): number {
    const { min, max } = this.chartBounds;
    const height = 290;
    const padTop = 16;
    const padBottom = 32;
    return height - padBottom - ((value - min) / Math.max(max - min, 1)) * (height - padTop - padBottom);
  }

  linePoints(series: Point[]): string {
    const width = 860;
    const padLeft = 60;
    const padRight = 14;
    const usableWidth = width - padLeft - padRight;
    const count = Math.max(series.length - 1, 1);
    return series.map((point, index) => {
      const x = padLeft + (index / count) * usableWidth;
      return `${x},${this.valueY(point.value)}`;
    }).join(' ');
  }

  lineColor(index: number): string {
    return colors[index % colors.length];
  }

  toggleSymbol(code: string): void {
    if (this.symbols.includes(code)) {
      if (this.symbols.length > 1) this.symbols = this.symbols.filter((item) => item !== code);
      return;
    }
    if (this.symbols.length < 3) this.symbols = [...this.symbols, code];
  }

  addSymbol(): void {
    const code = this.symbolInput.trim().toUpperCase();
    if (!/^\d{4,6}$/.test(code)) {
      this.error = '請輸入 4 至 6 位數字的台股代碼。';
      return;
    }
    if (this.symbols.includes(code)) {
      this.error = `${code} 已在比較清單中。`;
      return;
    }
    if (this.symbols.length >= 3) {
      this.error = '一次最多比較 3 檔，請先移除一檔再新增。';
      return;
    }
    this.error = '';
    this.symbols = [...this.symbols, code];
    this.symbolInput = '';
  }

  onSymbolInputChange(value: string): void {
    this.symbolInput = value.replace(/[^0-9]/g, '').slice(0, 6);
  }

  addPreset(code: string): void {
    if (!this.symbols.includes(code) && this.symbols.length < 3) this.symbols = [...this.symbols, code];
  }

  async runBacktest(): Promise<void> {
    if (this.isInvalid() || this.busy || this.symbols.length === 0) return;
    this.error = '';
    this.response = null;
    if (!this.apiBaseUrl) {
      this.error = '回測 API 尚未設定。請在 GitHub 專案設定 BACKEND_API_URL，指向已部署的 Spring Boot 後端。';
      return;
    }

    this.busy = true;
    const payload = {
      symbol: this.symbols[0],
      symbols: this.symbols,
      strategy: this.strategy,
      from: `${this.fromYear}-01-01`,
      to: `${this.toYear}-12-31`,
      fastWindow: Number(this.fast),
      slowWindow: Number(this.slow),
      rsiWindow: Number(this.rsiWindow),
      rsiBuyThreshold: Number(this.rsiBuy),
      rsiSellThreshold: Number(this.rsiSell),
      bollingerWindow: Number(this.bollingerWindow),
      bollingerMultiplier: Number(this.bollingerMultiplier),
      breakoutWindow: Number(this.breakoutWindow),
      drawdownBuyPercent: Number(this.drawdownBuy),
      profitSellPercent: Number(this.profitSell),
      monthlyContribution: Number(this.monthly),
      initialCapital: Number(this.initial),
      commissionRate: Number(this.commission) / 100,
      sellTaxRate: Number(this.tax || '0.1') / 100,
      useMarketTaxDefaults: !this.tax.trim(),
    };

    try {
      this.response = await firstValueFrom(this.http.post<BacktestResponse>(`${this.apiBaseUrl}/api/v1/backtests`, payload));
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse) {
        const message = (error.error as { error?: string } | null)?.error;
        this.error = message || (error.status === 0 ? '無法連線到回測服務，請確認 Spring Boot API 已啟動且允許此網站的跨網域請求。' : '回測暫時無法完成，請稍後再試。');
      } else {
        this.error = '回測暫時無法完成，請稍後再試。';
      }
    } finally {
      this.busy = false;
    }
  }
}
