import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

type DraftConfig = { symbols: string[]; strategy: string; fromYear: string; toYear: string };
type DraftVersion = { id: string; version: number; savedAt: string; config: DraftConfig };
type Draft = { id: string; name: string; versions: DraftVersion[] };
type JournalEntry = { id: string; createdAt: string; question: string; hypothesis: string; failureCondition: string; reflection: string; draftId: string; versionId: string };
type JournalStore = { schemaVersion: 1; entries: JournalEntry[] };

const JOURNAL_KEY = 'twse-strategy-lab.research-journal';
const DRAFT_KEY = 'twse-strategy-lab.config-drafts';
const STRATEGY_NAMES: Record<string, string> = {
  'ma-crossover': '雙均線交叉', 'rsi-reversion': 'RSI 均值回歸', 'bollinger-reversion': '布林通道回歸',
  breakout: '區間突破', 'drawdown-entry': '回跌買進 / 獲利出場',
};

@Component({
  selector: 'app-research-journal',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <section class="page-wrap subpage journal-page">
      <p class="eyebrow">研究筆記 · 此瀏覽器本機</p>
      <h1 tabindex="-1">留下當時的想法。</h1>
      <p class="subpage-lede">先記下問題、假設和什麼結果會讓你改變看法；研究後再補上反思。筆記幫你保留脈絡，不替你下投資結論。</p>
      <aside class="journal-privacy"><strong>僅儲存在此瀏覽器</strong><span>沒有登入或雲端備份。筆記文字可能包含個人投資計畫；清除瀏覽器資料會移除筆記。這裡不保存行情、回測結果或績效。</span></aside>
      <p *ngIf="loadError" class="notice-error" role="alert">{{loadError}} 原始資料未修改，筆記目前以唯讀方式保留。</p>
      <p *ngIf="saveError" class="notice-error" role="alert">{{saveError}}</p>
      <p *ngIf="savedNotice" class="journal-saved" role="status" aria-live="polite">{{savedNotice}}</p>

      <div class="journal-layout" *ngIf="!loadError">
        <section class="journal-compose" aria-labelledby="journal-form-title">
          <p class="eyebrow">研究前</p><h2 id="journal-form-title">先寫下可檢驗的想法</h2>
          <form (ngSubmit)="createEntry()">
            <label>我想弄清楚什麼？<textarea name="question" [(ngModel)]="question" maxlength="240" rows="2" placeholder="例如：這條均線規則在不同投入方式下差異多大？" [disabled]="!writable"></textarea><small>{{question.length}} / 240</small></label>
            <label>我目前的假設<textarea name="hypothesis" [(ngModel)]="hypothesis" maxlength="500" rows="3" placeholder="寫下你預期會看到的現象，以及原因。" [disabled]="!writable"></textarea><small>{{hypothesis.length}} / 500</small></label>
            <label>什麼情況會讓我改變看法？<textarea name="failureCondition" [(ngModel)]="failureCondition" maxlength="500" rows="2" placeholder="例如：換一段期間或加入成本後，差異就消失。" [disabled]="!writable"></textarea><small>{{failureCondition.length}} / 500</small></label>
            <label *ngIf="draftOptions.length">連結一份已保存的研究設定（選填）<select name="draftVersion" [(ngModel)]="draftVersionKey" [disabled]="!writable"><option value="">稍後再選</option><option *ngFor="let option of draftOptions" [value]="option.draft.id + ':' + option.version.id">{{option.draft.name}} · v{{option.version.version}} · {{option.version.config.symbols.join('、')}} {{strategyName(option.version.config.strategy)}}</option></select></label>
            <p *ngIf="!draftOptions.length" class="journal-hint">保存過工作台設定後，可以在建立筆記時連結版本。</p>
            <button class="button-primary" type="submit" [disabled]="!writable || !question.trim() || !hypothesis.trim() || entries.length >= maxEntries">{{entries.length >= maxEntries ? '筆記已達上限' : '保存研究前想法'}}</button>
            <small class="journal-hint">問題與假設保存後不可修改；研究後反思可以補充或更新。最多 {{maxEntries}} 則。</small>
          </form>
        </section>
        <section class="journal-list" aria-labelledby="journal-list-title">
          <div class="journal-list-heading"><div><p class="eyebrow">研究後</p><h2 id="journal-list-title">你的研究紀錄</h2></div><span>{{entries.length}} / {{maxEntries}}</span></div>
          <div *ngIf="!entries.length" class="journal-empty"><span aria-hidden="true">✳</span><strong>第一則筆記從一個問題開始</strong><p>記下原先想法，研究後回來補充觀察；先不用把每個欄位都寫得完整。</p></div>
          <article class="journal-entry" *ngFor="let entry of newestFirst">
            <div class="journal-entry-top"><time>{{entry.createdAt | date:'yyyy/MM/dd HH:mm'}}</time><button type="button" class="journal-delete" [disabled]="!writable" (click)="pendingDeleteId = entry.id">刪除此筆記</button></div>
            <div *ngIf="pendingDeleteId === entry.id" class="journal-confirm" role="group" aria-label="確認刪除筆記"><span>刪除後無法復原，要繼續嗎？</span><button type="button" class="journal-delete-confirm" (click)="deleteEntry(entry)">確認刪除</button><button type="button" class="journal-delete-cancel" (click)="pendingDeleteId = ''">取消</button></div>
            <h3>{{entry.question}}</h3><div class="journal-thought"><span>當時的假設</span><p>{{entry.hypothesis}}</p></div>
            <div class="journal-thought"><span>改變看法的條件</span><p>{{entry.failureCondition || '尚未設定'}}</p></div>
            <a *ngIf="entry.draftId && linkedVersion(entry) as linked" class="journal-linked" [routerLink]="'/backtest'" [queryParams]="{draft:entry.draftId,version:entry.versionId}">研究設定：{{linked.draft.name}} · v{{linked.version.version}} · {{linked.version.config.symbols.join('、')}} · {{strategyName(linked.version.config.strategy)}} →</a>
            <div *ngIf="entry.draftId && !linkedVersion(entry)" class="journal-hint">原連結設定已不存在；筆記仍保留。</div>
            <label class="reflection-label">研究後，我的觀察<textarea [name]="'reflection-'+entry.id" [(ngModel)]="entry.reflection" maxlength="1000" rows="3" placeholder="結果有沒有符合預期？哪些限制改變了你的解讀？" [disabled]="!writable" (blur)="saveReflection(entry)"></textarea><small>離開欄位時保存 · {{entry.reflection.length}} / 1000</small></label>
          </article>
        </section>
      </div>
      <p class="journal-footnote"><a routerLink="/methods">回測方法與資料限制 →</a> · 所有結果都須依資料範圍和模型假設解讀。</p>
    </section>
  `,
})
export class ResearchJournalComponent {
  readonly maxEntries = 50;
  question = '';
  hypothesis = '';
  failureCondition = '';
  draftVersionKey = '';
  entries: JournalEntry[] = [];
  draftOptions: { draft: Draft; version: DraftVersion }[] = [];
  loadError = '';
  saveError = '';
  savedNotice = '';
  writable = true;
  pendingDeleteId = '';
  private saveTimer?: ReturnType<typeof setTimeout>;

  constructor() { this.load(); }

  get newestFirst(): JournalEntry[] { return [...this.entries].sort((a, b) => b.createdAt.localeCompare(a.createdAt)); }
  strategyName(id: string): string { return STRATEGY_NAMES[id] ?? '研究設定'; }

  private load(): void {
    try {
      const rawDrafts = localStorage.getItem(DRAFT_KEY);
      if (rawDrafts) {
        const parsedDrafts: unknown = JSON.parse(rawDrafts);
        if (!this.validDraftStore(parsedDrafts)) throw new Error('研究設定格式無效。');
        this.draftOptions = parsedDrafts.drafts.flatMap((draft) => draft.versions.map((version) => ({ draft, version })))
          .sort((a, b) => b.version.savedAt.localeCompare(a.version.savedAt));
      }
      const rawJournal = localStorage.getItem(JOURNAL_KEY);
      if (rawJournal) {
        const parsedJournal: unknown = JSON.parse(rawJournal);
        if (!this.validJournalStore(parsedJournal)) throw new Error('筆記格式無效或版本不支援。');
        this.entries = parsedJournal.entries;
      }
    } catch {
      this.writable = false;
      this.loadError = '無法讀取本機筆記或研究設定。';
    }
  }

  private validDraftStore(value: unknown): value is { schemaVersion: 1; drafts: Draft[] } {
    if (!this.record(value) || value['schemaVersion'] !== 1 || !Array.isArray(value['drafts'])) return false;
    return value['drafts'].every((draft) => this.record(draft) && typeof draft['id'] === 'string' && typeof draft['name'] === 'string' && Array.isArray(draft['versions'])
      && draft['versions'].every((version) => this.record(version) && typeof version['id'] === 'string' && Number.isInteger(version['version'])
        && typeof version['savedAt'] === 'string' && Number.isFinite(Date.parse(version['savedAt']))
        && this.record(version['config']) && Array.isArray(version['config']['symbols']) && typeof version['config']['strategy'] === 'string'));
  }

  private validJournalStore(value: unknown): value is JournalStore {
    if (!this.record(value) || value['schemaVersion'] !== 1 || !Array.isArray(value['entries']) || value['entries'].length > this.maxEntries) return false;
    const ids = new Set<string>();
    return value['entries'].every((entry) => this.record(entry) && typeof entry['id'] === 'string' && !!entry['id'] && !ids.has(entry['id'])
      && (ids.add(entry['id']), typeof entry['createdAt'] === 'string' && Number.isFinite(Date.parse(entry['createdAt']))
        && ['question','hypothesis','failureCondition','reflection','draftId','versionId'].every((key) => typeof entry[key] === 'string')
        && entry['question'].length <= 240 && entry['hypothesis'].length <= 500 && entry['failureCondition'].length <= 500 && entry['reflection'].length <= 1000));
  }

  private record(value: unknown): value is Record<string, any> { return typeof value === 'object' && value !== null && !Array.isArray(value); }

  createEntry(): void {
    this.saveError = '';
    if (!this.writable || !this.question.trim() || !this.hypothesis.trim() || this.entries.length >= this.maxEntries) return;
    const [draftId = '', versionId = ''] = this.draftVersionKey.split(':');
    const entry: JournalEntry = { id: crypto.randomUUID(), createdAt: new Date().toISOString(), question: this.question.trim(), hypothesis: this.hypothesis.trim(), failureCondition: this.failureCondition.trim(), reflection: '', draftId, versionId };
    const next = { schemaVersion: 1 as const, entries: [...this.entries, entry] };
    if (!this.persist(next)) return;
    this.entries = next.entries;
    this.question = ''; this.hypothesis = ''; this.failureCondition = ''; this.draftVersionKey = '';
    this.savedNotice = '研究前想法已保存在此瀏覽器。';
  }

  saveReflection(entry: JournalEntry): void {
    if (!this.writable) return;
    if (this.persist({ schemaVersion: 1, entries: this.entries })) this.savedNotice = '研究後反思已保存。';
  }

  deleteEntry(entry: JournalEntry): void {
    if (!this.writable || this.pendingDeleteId !== entry.id) return;
    const next = { schemaVersion: 1 as const, entries: this.entries.filter((item) => item.id !== entry.id) };
    if (this.persist(next)) { this.entries = next.entries; this.pendingDeleteId = ''; this.savedNotice = '筆記已刪除。'; }
  }

  private persist(value: JournalStore): boolean {
    try { localStorage.setItem(JOURNAL_KEY, JSON.stringify(value)); this.saveError = ''; return true; }
    catch { this.saveError = '瀏覽器儲存失敗；這次變更未保存。請確認儲存空間，並先複製尚未保存的文字。'; return false; }
  }

  linkedVersion(entry: JournalEntry): { draft: Draft; version: DraftVersion } | undefined {
    return this.draftOptions.find((option) => option.draft.id === entry.draftId && option.version.id === entry.versionId);
  }
}
