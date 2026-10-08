import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

type StrategyId = 'ma-crossover' | 'rsi-reversion' | 'bollinger-reversion' | 'breakout' | 'drawdown-entry';
type Point = { date: string; value: number };
type Metric = number | null | undefined;
type AssetPeriod = {
  symbol: string;
  from?: string;
  to?: string;
  tradingDays?: number;
  requestedFrom?: string;
  requestedTo?: string;
  coverageStatus?: string;
  marketStatus?: string;
};
type BacktestMetadata = {
  backtestId?: string;
  engineVersion?: string;
  strategyVersion?: string;
  executionModel?: string;
  executionModelVersion?: string;
  costModelVersion?: string;
  priceAdjustmentPolicy?: string;
  marketRulesVersion?: string;
  metricsVersion?: string;
  datasetHash?: string;
  reproducibilityStatus?: string;
  resolvedConfig?: Record<string, unknown>;
  datasets?: Record<string, unknown>[];
};
type StrategyResult = {
  key: string;
  name: string;
  endingValue: Metric;
  contributed: Metric;
  profit?: Metric;
  totalReturn: Metric;
  timeWeightedReturn?: Metric;
  annualizedReturn: Metric;
  annualizedRealizedVolatility?: Metric;
  annualizationBasis?: string;
  maxDrawdown: Metric;
  series: Point[];
  trades?: Record<string, unknown>[];
  dailyEquity?: Record<string, unknown>[];
};
type BacktestResponse = {
  symbol: string;
  symbols: string[];
  from: string;
  to: string;
  tradingDays: number;
  assets?: AssetPeriod[];
  requestedFrom?: string;
  requestedTo?: string;
  status?: string;
  releaseStatus?: string;
  limitations?: { code: string; message: string }[];
  metadata?: BacktestMetadata;
  dataSource: string;
  assumptions: string[];
  results: StrategyResult[];
};
type StrategyOption = { id: StrategyId; name: string; summary: string; category: string };
type SavedConfig = {
  symbols: string[];
  symbolInput: string;
  strategy: StrategyId;
  fromYear: string;
  toYear: string;
  fast: string;
  slow: string;
  rsiWindow: string;
  rsiBuy: string;
  rsiSell: string;
  bollingerWindow: string;
  bollingerMultiplier: string;
  breakoutWindow: string;
  drawdownBuy: string;
  profitSell: string;
  monthly: string;
  initial: string;
  commission: string;
  tax: string;
};
type SavedVersion = { id: string; version: number; savedAt: string; config: SavedConfig };
type SavedDraft = { id: string; name: string; versions: SavedVersion[] };
type SavedDraftStore = { schemaVersion: 1; drafts: SavedDraft[] };
type CatalogItem = { code: string; name: string; kind: 'STOCK' | 'FUND' | string; asOf: string };
type CatalogSource = {
  id: string; title: string; provider: string; datasetUrl: string; resourceUrl: string;
  license: string; licenseUrl: string; updateFrequency: string; fetchedAt: string;
  asOfFrom: string; asOfTo: string; cacheTtlSeconds: number;
};
type CatalogResponse = {
  query: string; limit: number; totalMatches: number; items: CatalogItem[];
  sources: CatalogSource[]; limitations: string[];
};

const savedDraftKey = 'twse-strategy-lab.config-drafts';
const strategyIds: StrategyId[] = ['ma-crossover', 'rsi-reversion', 'bollinger-reversion', 'breakout', 'drawdown-entry'];

const colors = ['#21c7a8', '#b3c2d4', '#f0a84b', '#6f8fe8', '#d780a8', '#73a86c'];

@Component({
  selector: 'app-backtest-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
})
export class BacktestWorkspaceComponent {
  private readonly http = inject(HttpClient);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly route = inject(ActivatedRoute);
  readonly apiBaseUrl = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');
  private chartCacheFor: BacktestResponse | null = null;
  private chartCacheDates: string[] = [];
  private chartCacheBounds = { min: 0, max: 1 };
  readonly yearOptions = Array.from({ length: new Date().getFullYear() - 2010 + 1 }, (_, index) => String(2010 + index));
  readonly strategyOptions: StrategyOption[] = [
    { id: 'ma-crossover', name: '雙均線交叉', summary: '短均線由下向上穿越長均線買進；反向交叉賣出', category: '趨勢' },
    { id: 'rsi-reversion', name: 'RSI 均值回歸', summary: '簡單滾動 RSI 低於門檻買進，高於門檻賣出', category: '均值回歸' },
    { id: 'bollinger-reversion', name: '布林通道回歸', summary: '收盤低於下軌買進，大於或等於中線賣出', category: '均值回歸' },
    { id: 'breakout', name: '區間突破', summary: '收盤高於前 N 筆收盤高點買進，低於均線賣出', category: '突破' },
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
  private requestedPeriod: { from: string; to: string } | null = null;
  draftName = '';
  selectedDraftId = '';
  draftStore: SavedDraftStore = { schemaVersion: 1, drafts: [] };
  draftError = '';
  private draftStoreWritable = true;
  catalogQuery = '';
  catalogNameQuery = '';
  catalogResults: CatalogResponse | null = null;
  catalogBusy = false;
  catalogError = '';
  catalogMessage = '';
  private catalogRequestId = 0;
  private catalogDebounce?: ReturnType<typeof setTimeout>;
  private loadedDraftQuery = '';

  constructor() {
    this.readDrafts();
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const requestedSymbols = (params.get('symbols') ?? params.get('symbol') ?? '').split(',').map((code) => code.trim());
      const validSymbols = requestedSymbols.filter((code) => /^\d{4,6}$/.test(code)).slice(0, 3);
      if (validSymbols.length) this.symbols = [...new Set(validSymbols)];
      const requestedStrategy = params.get('strategy');
      if (requestedStrategy && strategyIds.includes(requestedStrategy as StrategyId)) this.strategy = requestedStrategy as StrategyId;
      const draftId = params.get('draft') ?? '';
      const versionId = params.get('version') ?? '';
      const draftSelection = `${draftId}:${versionId}`;
      if (draftId && draftSelection !== this.loadedDraftQuery) {
        const draft = this.draftStore.drafts.find((item) => item.id === draftId);
        const selected = draft?.versions.find((version) => version.id === versionId)
          ?? draft?.versions.reduce((current, version) => version.version > current.version ? version : current);
        if (draft && selected) { this.loadDraftVersion(draft, selected); this.loadedDraftQuery = draftSelection; }
      } else if (!draftId) this.loadedDraftQuery = '';
      this.changeDetector.markForCheck();
    });
  }

  private readDrafts(): void {
    try {
      const raw = window.localStorage.getItem(savedDraftKey);
      if (raw === null) return;
      const parsed: unknown = JSON.parse(raw);
      if (!this.isDraftStore(parsed)) throw new Error('無法辨識的設定格式或版本');
      this.draftStore = parsed;
    } catch {
      this.draftStoreWritable = false;
      this.draftError = '無法讀取本機設定（資料格式無效、版本不支援或瀏覽器拒絕存取）。原有資料已保留；修復或匯出前不會寫入，以免覆蓋可恢復資料。';
    }
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }

  private hasExactKeys(value: Record<string, unknown>, keys: string[]): boolean {
    const actual = Object.keys(value).sort();
    return actual.length === keys.length && actual.every((key, index) => key === [...keys].sort()[index]);
  }

  private isSavedConfig(value: unknown): value is SavedConfig {
    if (!this.isRecord(value)) return false;
    const config = value;
    const configKeys = ['symbols', 'symbolInput', 'strategy', 'fromYear', 'toYear', 'fast', 'slow', 'rsiWindow', 'rsiBuy', 'rsiSell',
      'bollingerWindow', 'bollingerMultiplier', 'breakoutWindow', 'drawdownBuy', 'profitSell', 'monthly', 'initial', 'commission', 'tax'];
    if (!this.hasExactKeys(config, configKeys)) return false;
    const strings = ['symbolInput', 'fromYear', 'toYear', 'fast', 'slow', 'rsiWindow', 'rsiBuy', 'rsiSell',
      'bollingerWindow', 'bollingerMultiplier', 'breakoutWindow', 'drawdownBuy', 'profitSell', 'monthly', 'initial', 'commission', 'tax'];
    if (!strings.every((key) => typeof config[key] === 'string')) return false;
    if (!Array.isArray(config['symbols']) || config['symbols'].length < 1 || config['symbols'].length > 3
      || !config['symbols'].every((item) => typeof item === 'string' && /^\d{4,6}$/.test(item))
      || new Set(config['symbols']).size !== config['symbols'].length) return false;
    if (typeof config['symbolInput'] !== 'string' || !/^\d{0,6}$/.test(config['symbolInput'])
      || typeof config['strategy'] !== 'string' || !strategyIds.includes(config['strategy'] as StrategyId)) return false;
    const numericRules: [string, number, number, boolean?][] = [
      ['fromYear', 2010, new Date().getFullYear(), true], ['toYear', 2010, new Date().getFullYear(), true],
      ['fast', 2, 250, true], ['slow', 3, 500, true], ['rsiWindow', 2, 100, true], ['rsiBuy', 1, 49], ['rsiSell', 51, 99],
      ['bollingerWindow', 2, 200, true], ['bollingerMultiplier', 0.5, 5], ['breakoutWindow', 2, 250, true],
      ['drawdownBuy', 1, 80], ['profitSell', 1, 200], ['monthly', 0, Number.MAX_VALUE], ['initial', 0.01, Number.MAX_VALUE],
      ['commission', 0, 99.999999],
    ];
    for (const [key, min, max, integer] of numericRules) {
      const raw = config[key] as string;
      const number = Number(raw);
      if (!raw.trim() || !Number.isFinite(number) || number < min || number > max || (integer && !Number.isInteger(number))) return false;
    }
    if (Number(config['fromYear']) > Number(config['toYear'])) return false;
    if (config['strategy'] === 'ma-crossover' && Number(config['fast']) >= Number(config['slow'])) return false;
    const tax = config['tax'];
    if (typeof tax !== 'string' || (tax !== '' && (!/^\d+(\.\d+)?$/.test(tax) || !Number.isFinite(Number(tax)) || Number(tax) < 0 || Number(tax) >= 100))) return false;
    return true;
  }

  private isDraftStore(value: unknown): value is SavedDraftStore {
    if (!this.isRecord(value) || !this.hasExactKeys(value, ['schemaVersion', 'drafts']) || value['schemaVersion'] !== 1 || !Array.isArray(value['drafts'])) return false;
    const draftIds = new Set<string>();
    for (const item of value['drafts']) {
      if (!this.isRecord(item) || !this.hasExactKeys(item, ['id', 'name', 'versions']) || typeof item['id'] !== 'string' || !item['id'] || draftIds.has(item['id'])
        || typeof item['name'] !== 'string' || !item['name'].trim() || item['name'].length > 60 || !Array.isArray(item['versions']) || !item['versions'].length) return false;
      draftIds.add(item['id']);
      const versionNumbers = new Set<number>();
      const versionIds = new Set<string>();
      for (const version of item['versions']) {
        if (!this.isRecord(version) || !this.hasExactKeys(version, ['id', 'version', 'savedAt', 'config']) || typeof version['id'] !== 'string' || !version['id']
          || versionIds.has(version['id'])
          || !Number.isInteger(version['version']) || Number(version['version']) < 1 || versionNumbers.has(Number(version['version']))
          || typeof version['savedAt'] !== 'string' || !Number.isFinite(Date.parse(version['savedAt'])) || !this.isSavedConfig(version['config'])) return false;
        versionIds.add(version['id']);
        versionNumbers.add(Number(version['version']));
      }
    }
    return true;
  }

  private captureConfig(): SavedConfig {
    return {
      symbols: [...this.symbols], symbolInput: this.symbolInput, strategy: this.strategy,
      fromYear: String(this.fromYear), toYear: String(this.toYear), fast: String(this.fast), slow: String(this.slow),
      rsiWindow: String(this.rsiWindow), rsiBuy: String(this.rsiBuy), rsiSell: String(this.rsiSell),
      bollingerWindow: String(this.bollingerWindow), bollingerMultiplier: String(this.bollingerMultiplier),
      breakoutWindow: String(this.breakoutWindow), drawdownBuy: String(this.drawdownBuy), profitSell: String(this.profitSell),
      monthly: String(this.monthly), initial: String(this.initial), commission: String(this.commission), tax: String(this.tax ?? ''),
    };
  }

  private newId(): string {
    return typeof crypto !== 'undefined' && 'randomUUID' in crypto
      ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  saveDraftVersion(): void {
    this.draftError = '';
    if (!this.draftStoreWritable) return;
    const name = this.draftName.trim();
    if (!name || name.length > 60) {
      this.draftError = '請輸入 1 至 60 個字的設定名稱。';
      return;
    }
    const config = this.captureConfig();
    if (!this.isSavedConfig(config)) {
      this.draftError = '目前表單有無效欄位，請修正後再保存。';
      return;
    }
    const existing = this.draftStore.drafts.find((draft) => draft.name.toLocaleLowerCase() === name.toLocaleLowerCase());
    const target = existing ?? { id: this.newId(), name, versions: [] };
    const nextVersion: SavedVersion = {
      id: this.newId(), version: Math.max(0, ...target.versions.map((version) => version.version)) + 1,
      savedAt: new Date().toISOString(), config,
    };
    const next: SavedDraftStore = {
      schemaVersion: 1,
      drafts: existing
        ? this.draftStore.drafts.map((draft) => draft.id === target.id ? { ...draft, versions: [...draft.versions, nextVersion] } : draft)
        : [...this.draftStore.drafts, { ...target, versions: [nextVersion] }],
    };
    if (this.persistDrafts(next)) {
      this.draftStore = next;
      this.selectedDraftId = target.id;
      this.draftName = name;
    }
  }

  loadDraftVersion(draft: SavedDraft, version: SavedVersion): void {
    this.draftError = '';
    if (!this.isSavedConfig(version.config)) {
      this.draftStoreWritable = false;
      this.draftError = '這個版本的設定欄位驗證失敗；未載入也未修改儲存資料。';
      return;
    }
    Object.assign(this, version.config);
    this.selectedDraftId = draft.id;
    this.draftName = draft.name;
    this.response = null;
    this.error = '';
  }

  deleteDraftVersion(draft: SavedDraft, version: SavedVersion): void {
    this.draftError = '';
    if (!this.draftStoreWritable) return;
    const drafts = this.draftStore.drafts.map((item) => item.id !== draft.id ? item : {
      ...item, versions: item.versions.filter((saved) => saved.id !== version.id),
    }).filter((item) => item.versions.length > 0);
    const next: SavedDraftStore = { schemaVersion: 1, drafts };
    if (this.persistDrafts(next)) {
      this.draftStore = next;
      if (!drafts.some((item) => item.id === this.selectedDraftId)) this.selectedDraftId = '';
    }
  }

  private persistDrafts(next: SavedDraftStore): boolean {
    if (!this.isDraftStore(next)) {
      this.draftError = '設定驗證失敗，未寫入本機儲存。';
      return false;
    }
    try {
      const serialized = JSON.stringify(next);
      window.localStorage.setItem(savedDraftKey, serialized);
      return true;
    } catch {
      this.draftError = '瀏覽器儲存空間不足或目前不可用；原有設定未變更，請刪除不需要的版本後重試。';
      return false;
    }
  }

  get selectedStrategy(): StrategyOption {
    return this.strategyOptions.find((item) => item.id === this.strategy) ?? this.strategyOptions[0];
  }

  get leadingResult(): StrategyResult | undefined {
    return this.response?.results.filter((item) => this.isMetric(item.endingValue))
      .sort((a, b) => Number(b.endingValue) - Number(a.endingValue))[0];
  }

  get requestedFrom(): string {
    return this.response?.requestedFrom ?? this.requestedPeriod?.from ?? '未提供';
  }

  get requestedTo(): string {
    return this.response?.requestedTo ?? this.requestedPeriod?.to ?? '未提供';
  }

  get assetPeriods(): AssetPeriod[] {
    if (!this.response) return [];
    if (this.response.assets?.length) return this.response.assets;
    // Legacy top-level dates describe only the first asset, never every asset.
    return this.response.symbols.map((symbol, index) => ({
      symbol,
      from: index === 0 ? this.response?.from : undefined,
      to: index === 0 ? this.response?.to : undefined,
      tradingDays: index === 0 ? this.response?.tradingDays : undefined,
    }));
  }

  get chartDates(): string[] {
    this.refreshChartCache();
    return this.chartCacheDates;
  }

  get chartTicks(): number[] {
    const { min, max } = this.chartBounds;
    return this.chartCacheDates.length ? [min, (min + max) / 2, max] : [];
  }

  get chartBounds(): { min: number; max: number } {
    this.refreshChartCache();
    return this.chartCacheBounds;
  }

  private refreshChartCache(): void {
    if (this.chartCacheFor === this.response) return;
    this.chartCacheFor = this.response;
    const values = this.response?.results.flatMap((result) => result.series.map((point) => point.value)).filter(Number.isFinite) ?? [];
    this.chartCacheDates = [...new Set(this.response?.results.flatMap((result) => result.series.map((point) => point.date)) ?? [])]
      .filter((date) => Number.isFinite(Date.parse(date))).sort();
    this.chartCacheBounds = values.length
      ? { min: Math.min(...values) * 0.94, max: Math.max(...values) * 1.04 }
      : { min: 0, max: 1 };
  }

  get chartFirstDate(): string {
    return this.chartDates[0] ?? '';
  }

  get chartLastDate(): string {
    const dates = this.chartDates;
    return dates[dates.length - 1] ?? '';
  }

  isInvalid(): boolean {
    return Number(this.fromYear) > Number(this.toYear)
      || !this.inRange(this.initial, 0.01, Number.MAX_VALUE)
      || !this.inRange(this.monthly, 0, Number.MAX_VALUE)
      || !Number.isFinite(this.rateFromPercent(this.commission))
      || (String(this.tax ?? '').trim() !== '' && !Number.isFinite(this.rateFromPercent(this.tax)))
      || !this.inRange(this.fast, 2, 250, true)
      || !this.inRange(this.slow, 3, 500, true)
      || !this.inRange(this.rsiWindow, 2, 100, true)
      || !this.inRange(this.rsiBuy, 1, 49)
      || !this.inRange(this.rsiSell, 51, 99)
      || !this.inRange(this.bollingerWindow, 2, 200, true)
      || !this.inRange(this.bollingerMultiplier, 0.5, 5)
      || !this.inRange(this.breakoutWindow, 2, 250, true)
      || !this.inRange(this.drawdownBuy, 1, 80)
      || !this.inRange(this.profitSell, 1, 200)
      || (this.strategy === 'ma-crossover' && Number(this.fast) >= Number(this.slow));
  }

  private inRange(value: string, min: number, max: number, integer = false): boolean {
    const number = Number(value);
    return String(value ?? '').trim() !== '' && Number.isFinite(number)
      && number >= min && number <= max && (!integer || Number.isInteger(number));
  }

  private rateFromPercent(value: string): number {
    const percent = Number(value);
    if (String(value ?? '').trim() === '' || !Number.isFinite(percent) || percent < 0 || percent >= 100) return NaN;
    // The API accepts rates with at most eight decimal places. Explicitly
    // round the percent conversion so JSON does not expose binary tails.
    const rate = Number((percent / 100).toFixed(8));
    return rate < 1 ? rate : NaN;
  }

  isMetric(value: Metric): value is number {
    return typeof value === 'number' && Number.isFinite(value);
  }

  formatMoney(value: Metric): string {
    if (!this.isMetric(value)) return '無法計算';
    return new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 }).format(value);
  }

  formatAmount(value: Metric): string {
    return this.isMetric(value) ? `$${this.formatMoney(value)}` : '無法計算';
  }

  formatPercent(value: Metric): string {
    if (!this.isMetric(value)) return '無法計算';
    return `${value >= 0 ? '+' : '−'}${Math.abs(value).toFixed(1)}%`;
  }

  formatVolatility(value: Metric): string {
    return this.isMetric(value) ? `${value.toFixed(1)}%` : '無法計算';
  }

  profitOf(result: StrategyResult): Metric {
    if (result.profit !== undefined) return result.profit;
    return this.isMetric(result.endingValue) && this.isMetric(result.contributed)
      ? result.endingValue - result.contributed : undefined;
  }

  usesDailyTwr(result: StrategyResult): boolean {
    return result.annualizationBasis === 'DAILY_TWR_ACT_365_2425';
  }

  annualizedTwr(result: StrategyResult): Metric {
    return this.usesDailyTwr(result) ? result.annualizedReturn : undefined;
  }

  formatDrawdown(result: StrategyResult): string {
    return this.usesDailyTwr(result) && this.isMetric(result.maxDrawdown)
      ? `${result.maxDrawdown > 0 ? '−' : ''}${Math.abs(result.maxDrawdown).toFixed(1)}%` : '無法計算';
  }

  traceSample(rows: Record<string, unknown>[] | undefined): Record<string, unknown>[] {
    if (!rows) return [];
    return rows.length <= 6 ? rows : [...rows.slice(0, 3), ...rows.slice(-3)];
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
    const dates = this.chartDates;
    if (!dates.length) return '';
    const first = Date.parse(dates[0]);
    const span = Math.max(Date.parse(dates[dates.length - 1]) - first, 1);
    return series.filter((point) => Number.isFinite(Date.parse(point.date)) && Number.isFinite(point.value)).map((point) => {
      const x = padLeft + ((Date.parse(point.date) - first) / span) * usableWidth;
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
    this.onCatalogQueryChange(this.symbolInput);
  }

  onCatalogQueryChange(value: string): void {
    this.catalogQuery = value.trim().slice(0, 60);
    this.catalogMessage = '';
    this.catalogError = '';
    if (this.catalogDebounce) clearTimeout(this.catalogDebounce);
    const requestId = ++this.catalogRequestId;
    if (this.catalogQuery.trim().length < 2) {
      this.catalogResults = null;
      this.catalogBusy = false;
      this.changeDetector.markForCheck();
      return;
    }
    this.catalogResults = null;
    this.catalogBusy = true;
    this.changeDetector.markForCheck();
    this.catalogDebounce = setTimeout(() => void this.searchCatalog(this.catalogQuery, requestId), 350);
  }

  private async searchCatalog(query: string, requestId: number): Promise<void> {
    if (!this.apiBaseUrl) {
      if (requestId !== this.catalogRequestId) return;
      this.catalogBusy = false;
      this.catalogError = '目錄搜尋 API 尚未設定；仍可輸入已知的 4 至 6 位數字代碼。';
      this.changeDetector.markForCheck();
      return;
    }
    try {
      const result = await firstValueFrom(this.http.get<CatalogResponse>(`${this.apiBaseUrl}/api/v1/instruments`, {
        params: { query, limit: '20' },
      }));
      if (requestId !== this.catalogRequestId) return;
      this.catalogResults = result;
      this.catalogError = '';
      this.catalogBusy = false;
      this.changeDetector.markForCheck();
    } catch (error) {
      if (requestId !== this.catalogRequestId) return;
      this.catalogResults = null;
      this.catalogBusy = false;
      this.catalogError = error instanceof HttpErrorResponse && error.status === 0
        ? '目前無法連線至標的目錄，請稍後重試。'
        : '標的目錄查詢失敗，請稍後重試。';
      this.changeDetector.markForCheck();
    }
  }

  canAddCatalogItem(item: CatalogItem): boolean {
    return this.isCompatibleCatalogCode(item.code) && !this.symbols.includes(item.code) && this.symbols.length < 3;
  }

  isCompatibleCatalogCode(code: string): boolean {
    return /^\d{4,6}$/.test(code);
  }

  addCatalogItem(item: CatalogItem): void {
    if (!/^\d{4,6}$/.test(item.code)) {
      this.catalogMessage = `${item.code} 是基金目錄項目，但目前行情與回測介面只接受 4 至 6 位數字代碼，尚不能加入。`;
      return;
    }
    if (this.symbols.includes(item.code)) {
      this.catalogMessage = `${item.code} 已在比較清單中。`;
      return;
    }
    if (this.symbols.length >= 3) {
      this.catalogMessage = '一次最多比較 3 檔，請先移除一檔再新增。';
      return;
    }
    this.symbols = [...this.symbols, item.code];
    this.symbolInput = '';
    this.catalogNameQuery = '';
    this.catalogQuery = '';
    this.catalogResults = null;
    this.catalogMessage = `${item.code} ${item.name} 已加入比較清單。`;
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
    const taxInput = String(this.tax ?? '').trim();
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
      commissionRate: this.rateFromPercent(this.commission),
      sellTaxRate: this.rateFromPercent(taxInput || '0.1'),
      useMarketTaxDefaults: !taxInput,
    };
    this.requestedPeriod = { from: payload.from, to: payload.to };

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
      // Angular 22 uses zoneless change detection by default. The async HTTP
      // continuation must notify Angular after updating plain component fields.
      this.changeDetector.markForCheck();
    }
  }
}
