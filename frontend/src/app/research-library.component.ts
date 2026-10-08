import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

interface SavedConfigView { symbols: string[]; strategy: string; fromYear: string; toYear: string; fast: string; slow: string; rsiWindow: string; rsiBuy: string; rsiSell: string; bollingerWindow: string; bollingerMultiplier: string; breakoutWindow: string; drawdownBuy: string; profitSell: string; monthly: string; initial: string; commission: string; tax: string; }
interface SavedVersionView { id: string; version: number; savedAt: string; config: SavedConfigView; }
interface SavedDraftView { id: string; name: string; versions: SavedVersionView[]; }
const STORAGE_KEY = 'twse-strategy-lab.config-drafts';
const STRATEGY_NAMES: Record<string, string> = {
  'ma-crossover': '雙均線交叉', 'rsi-reversion': 'RSI 均值回歸', 'bollinger-reversion': '布林通道回歸',
  breakout: '區間突破', 'drawdown-entry': '回跌買進 / 獲利出場',
};
const COMPARE_FIELDS: { key: keyof SavedConfigView; label: string; suffix?: string }[] = [
  { key: 'symbols', label: '研究標的' }, { key: 'strategy', label: '策略' },
  { key: 'fromYear', label: '起始年度' }, { key: 'toYear', label: '結束年度' },
  { key: 'fast', label: '短均線' }, { key: 'slow', label: '長均線' },
  { key: 'rsiWindow', label: 'RSI 週期' }, { key: 'rsiBuy', label: 'RSI 買進門檻' }, { key: 'rsiSell', label: 'RSI 賣出門檻' },
  { key: 'bollingerWindow', label: '布林週期' }, { key: 'bollingerMultiplier', label: '布林標準差倍數' },
  { key: 'breakoutWindow', label: '突破回看筆數' }, { key: 'drawdownBuy', label: '回跌買進門檻' }, { key: 'profitSell', label: '獲利出場門檻' },
  { key: 'initial', label: '起始投入', suffix: ' 元' }, { key: 'monthly', label: '每月投入', suffix: ' 元' },
  { key: 'commission', label: '手續費率', suffix: '%' }, { key: 'tax', label: '賣出交易稅率', suffix: '%' },
];

@Component({
  selector: 'app-research-library',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page-wrap subpage research-library">
      <p class="eyebrow">MY RESEARCH · 此瀏覽器本機</p>
      <h1 tabindex="-1">讓研究設定有脈絡。</h1>
      <p class="subpage-lede">查看已保存的策略設定、版本差異，並載入舊版設定繼續研究。這裡保存的是參數，不是回測歷史或結果。</p>
      <div class="research-storage-note"><strong>只存在此瀏覽器</strong><span>沒有登入、雲端同步或跨裝置備份。清除瀏覽器資料可能移除設定；行情、交易明細、資產曲線和績效結果都不會保存。</span></div>
      <p *ngIf="error" class="notice-error" role="alert">{{error}}</p>
      <ng-container *ngIf="!error">
        <div class="research-empty" *ngIf="drafts.length === 0"><span aria-hidden="true">⌂</span><h2>還沒有保存的研究設定</h2><p>到回測工作台設定標的、策略與期間，再選擇「保存新版本」。</p><a class="button-primary" routerLink="/backtest" [queryParams]="{symbol:'0050',strategy:'ma-crossover'}">前往工作台建立第一份設定 →</a></div>
        <div class="research-draft-list" *ngIf="drafts.length > 0">
          <article class="research-draft" *ngFor="let draft of drafts">
            <div class="research-draft-heading"><div><span class="research-count">{{draft.versions.length}} 個版本</span><h2>{{draft.name}}</h2><p>{{symbols(draft.versions[draft.versions.length - 1].config)}} · {{strategyName(draft.versions[draft.versions.length - 1].config.strategy)}} · 最近保存 {{draft.versions[draft.versions.length - 1].savedAt | date:'yyyy/MM/dd HH:mm'}}</p></div><a class="button-outline" [routerLink]="'/backtest'" [queryParams]="{draft:draft.id,version:draft.versions[draft.versions.length - 1].id}">載入最新版本</a></div>
            <div class="version-list"><div class="version-row" *ngFor="let version of newestFirst(draft.versions)"><span class="version-pill">v{{version.version}}</span><span class="version-date">{{version.savedAt | date:'yyyy/MM/dd HH:mm'}}</span><span class="version-strategy">{{strategyName(version.config.strategy)}} · {{symbols(version.config)}}</span><a [routerLink]="'/backtest'" [queryParams]="{draft:draft.id,version:version.id}">載入這個版本 →</a></div></div>
          </article>
        </div>
        <section class="compare-settings" *ngIf="drafts.length > 0" aria-labelledby="compare-title">
          <div><p class="eyebrow">VERSION COMPARISON</p><h2 id="compare-title">比較同一份設定的兩個版本</h2><p>只比較使用者保存的輸入參數，不比較尚未保存的行情或回測結果。</p></div>
          <div class="compare-selectors"><label>研究設定<select [value]="selectedDraftId" (change)="chooseDraft($any($event.target).value)"><option *ngFor="let item of drafts" [value]="item.id">{{item.name}}</option></select></label><label>版本 A<select [value]="versionAId" (change)="versionAId = $any($event.target).value"><option *ngFor="let item of selectedDraft?.versions" [value]="item.id">v{{item.version}} · {{item.savedAt | date:'yyyy/MM/dd HH:mm'}}</option></select></label><label>版本 B<select [value]="versionBId" (change)="versionBId = $any($event.target).value"><option *ngFor="let item of selectedDraft?.versions" [value]="item.id">v{{item.version}} · {{item.savedAt | date:'yyyy/MM/dd HH:mm'}}</option></select></label></div>
          <div class="diff-table-wrap" *ngIf="versionA && versionB"><table class="diff-table"><thead><tr><th scope="col">設定</th><th scope="col">版本 A · v{{versionA.version}}</th><th scope="col">版本 B · v{{versionB.version}}</th></tr></thead><tbody><tr *ngFor="let field of differences"><th scope="row">{{field.label}}</th><td>{{display(versionA.config[field.key], field.suffix)}}</td><td>{{display(versionB.config[field.key], field.suffix)}}</td></tr><tr *ngIf="differences.length === 0"><td colspan="3" class="same-version">兩個版本的設定完全相同。</td></tr></tbody></table></div>
        </section>
      </ng-container>
    </section>
  `,
})
export class ResearchLibraryComponent {
  drafts: SavedDraftView[] = [];
  error = '';
  selectedDraftId = '';
  versionAId = '';
  versionBId = '';

  constructor() { this.readOnlyLoad(); this.resetComparison(); }

  get selectedDraft(): SavedDraftView | undefined { return this.drafts.find((draft) => draft.id === this.selectedDraftId); }
  get versionA(): SavedVersionView | undefined { return this.selectedDraft?.versions.find((version) => version.id === this.versionAId); }
  get versionB(): SavedVersionView | undefined { return this.selectedDraft?.versions.find((version) => version.id === this.versionBId); }
  get differences(): typeof COMPARE_FIELDS { return this.versionA && this.versionB ? COMPARE_FIELDS.filter((field) => JSON.stringify(this.versionA?.config[field.key]) !== JSON.stringify(this.versionB?.config[field.key])) : []; }

  private readOnlyLoad(): void {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const parsed: unknown = JSON.parse(raw);
      if (!this.validStore(parsed)) throw new Error('無法辨識本機設定格式；資料未修改。請返回工作台確認設定版本。');
      this.drafts = parsed.drafts;
    } catch (error) {
      this.error = error instanceof Error && error.message.includes('無法辨識') ? error.message : '目前無法讀取本機研究設定；資料未修改。請確認瀏覽器儲存空間權限。';
    }
  }

  private validStore(value: unknown): value is { schemaVersion: 1; drafts: SavedDraftView[] } {
    if (!this.record(value) || value['schemaVersion'] !== 1 || !Array.isArray(value['drafts']) || value['drafts'].length > 200) return false;
    const draftIds = new Set<string>();
    for (const rawDraft of value['drafts']) {
      if (!this.record(rawDraft) || typeof rawDraft['id'] !== 'string' || !rawDraft['id'] || draftIds.has(rawDraft['id']) || typeof rawDraft['name'] !== 'string' || !rawDraft['name'].trim() || rawDraft['name'].length > 60 || !Array.isArray(rawDraft['versions']) || rawDraft['versions'].length < 1 || rawDraft['versions'].length > 500) return false;
      draftIds.add(rawDraft['id']);
      const versionIds = new Set<string>(), numbers = new Set<number>();
      for (const rawVersion of rawDraft['versions']) {
        if (!this.record(rawVersion) || typeof rawVersion['id'] !== 'string' || !rawVersion['id'] || versionIds.has(rawVersion['id']) || !Number.isInteger(rawVersion['version']) || Number(rawVersion['version']) < 1 || numbers.has(Number(rawVersion['version'])) || typeof rawVersion['savedAt'] !== 'string' || !Number.isFinite(Date.parse(rawVersion['savedAt'])) || !this.validConfig(rawVersion['config'])) return false;
        versionIds.add(rawVersion['id']); numbers.add(Number(rawVersion['version']));
      }
    }
    return true;
  }

  private validConfig(value: unknown): value is SavedConfigView {
    if (!this.record(value)) return false;
    const keys = ['symbols','symbolInput','strategy','fromYear','toYear','fast','slow','rsiWindow','rsiBuy','rsiSell','bollingerWindow','bollingerMultiplier','breakoutWindow','drawdownBuy','profitSell','monthly','initial','commission','tax'];
    return Object.keys(value).length === keys.length && keys.every((key) => Object.hasOwn(value, key))
      && Array.isArray(value['symbols']) && value['symbols'].length >= 1 && value['symbols'].length <= 3 && value['symbols'].every((item) => typeof item === 'string' && /^\d{4,6}$/.test(item))
      && typeof value['strategy'] === 'string' && Object.hasOwn(STRATEGY_NAMES, value['strategy'])
      && keys.filter((key) => key !== 'symbols').every((key) => typeof value[key] === 'string');
  }

  private record(value: unknown): value is Record<string, any> { return typeof value === 'object' && value !== null && !Array.isArray(value); }
  chooseDraft(id: string): void { this.selectedDraftId = id; this.resetComparison(); }
  private resetComparison(): void { const versions = this.selectedDraft?.versions ?? this.drafts[0]?.versions ?? []; this.selectedDraftId ||= this.drafts[0]?.id ?? ''; this.versionAId = versions.length ? this.newestFirst(versions)[Math.min(1, versions.length - 1)].id : ''; this.versionBId = versions.length ? this.newestFirst(versions)[0].id : ''; }
  newestFirst(versions: SavedVersionView[]): SavedVersionView[] { return [...versions].sort((a, b) => b.version - a.version); }
  symbols(config: SavedConfigView): string { return config.symbols.join('、'); }
  strategyName(strategy: string): string { return STRATEGY_NAMES[strategy] ?? '未知策略'; }
  display(value: unknown, suffix = ''): string { return Array.isArray(value) ? value.join('、') : `${value ?? '—'}${suffix}`; }
}
