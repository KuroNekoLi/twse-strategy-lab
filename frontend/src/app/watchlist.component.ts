import { CommonModule } from '@angular/common';
import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { RouterLink } from '@angular/router';
import { WATCHLIST_MAX_ITEMS, WatchlistItem, WatchlistStore } from './watchlist-store';

type PageState = 'loading' | 'ready' | 'malformed' | 'unavailable';

@Component({
  selector: 'app-watchlist-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="page-wrap subpage watchlist-page">
      <p class="eyebrow">研究工具</p>
      <h1 tabindex="-1">觀察清單</h1>
      <p class="subpage-lede">在這部瀏覽器保存想研究的代碼，選擇 2 至 3 檔後帶入既有回測工作台。</p>

      <div class="watchlist-local-note"><strong>僅存在此瀏覽器</strong><span>不會同步到其他裝置或帳號。資料只保存代碼與加入時間；清除瀏覽器資料也會移除清單。</span></div>

      <p *ngIf="state === 'loading'" class="watchlist-state" role="status">正在讀取本機觀察清單…</p>
      <div *ngIf="state === 'malformed' || state === 'unavailable'" class="watchlist-error" role="alert">
        <strong>{{ state === 'malformed' ? '無法安全讀取清單' : '本機儲存空間無法使用' }}</strong>
        <span>{{ stateMessage }}</span>
        <button type="button" (click)="reload()">重新讀取</button>
      </div>

      <ng-container *ngIf="state === 'ready'">
        <form class="watchlist-add" (submit)="$event.preventDefault(); add()">
          <label for="watchlist-code">加入標的代碼</label>
          <div class="watchlist-add-row">
            <input id="watchlist-code" type="text" inputmode="numeric" autocomplete="off" maxlength="6"
              [value]="codeInput" (input)="codeInput = inputValue($event)"
              (keydown.enter)="$event.preventDefault(); add()" placeholder="例如 2330">
            <button type="submit" [disabled]="items.length >= maxItems">加入清單</button>
          </div>
          <small>接受 4 至 6 位數字代碼；此處只檢查格式，不查詢代碼是否存在或行情是否支援。最多 {{maxItems}} 檔。</small>
        </form>
        <p *ngIf="message" class="watchlist-message" [class.watchlist-message-error]="messageIsError"
          [attr.role]="messageIsError ? 'alert' : 'status'">{{message}}</p>

        <div *ngIf="items.length === 0" class="watchlist-empty">
          <strong>清單還是空的</strong>
          <p>輸入一個 4 至 6 位數字代碼開始追蹤；也可以先到標的目錄確認名稱與回測支援狀態。</p>
          <a routerLink="/explore">前往標的目錄 →</a>
        </div>

        <ng-container *ngIf="items.length > 0">
          <div class="watchlist-list-heading">
            <div><h2>已追蹤 {{items.length}} / {{maxItems}} 檔</h2><p>可勾選 2 至 3 檔，帶入工作台。</p></div>
            <span *ngIf="selectedCodes.length > 0">{{selectedCodes.length}} 檔已選</span>
          </div>
          <ul class="watchlist-items" aria-label="已追蹤標的">
            <li *ngFor="let item of items; trackBy: trackCode">
              <label class="watchlist-select">
                <input type="checkbox" [checked]="selectedCodes.includes(item.code)"
                  [disabled]="!selectedCodes.includes(item.code) && selectedCodes.length >= 3"
                  (change)="setSelected(item.code, $any($event.target).checked)">
                <span class="watchlist-code">{{item.code}}</span>
                <span class="watchlist-added">加入於 {{item.addedAt | date:'yyyy/MM/dd'}}</span>
              </label>
              <a [routerLink]="['/stocks', item.code]" class="watchlist-research">查看個股研究</a>
              <button type="button" class="watchlist-remove" [attr.aria-label]="'從觀察清單移除 ' + item.code"
                (click)="remove(item)">移除</button>
            </li>
          </ul>
          <p class="watchlist-run-note">帶入後使用工作台的共同策略與期間設定；各標的實際行情期間和結果會分別列示。</p>
          <button type="button" class="watchlist-run" [disabled]="selectedCodes.length < 2 || selectedCodes.length > 3"
            (click)="runBacktest()">用所選標的開始回測 <span aria-hidden="true">→</span></button>
          <p *ngIf="selectedCodes.length < 2" class="watchlist-selection-hint">請至少選擇 2 檔；最多可選 3 檔。</p>
        </ng-container>
      </ng-container>
    </section>
  `,
  styles: [`
    .watchlist-page{max-width:920px}
    .watchlist-page>.eyebrow{font-size:12px;letter-spacing:0;color:#8f3028}
    .watchlist-page>h1{font:600 38px/1.2 Georgia,"Noto Serif TC",serif;color:#242822;margin:0}
    .watchlist-page>.subpage-lede{font-size:14px;line-height:1.7;color:#62675f;margin:10px 0 20px}
    .watchlist-local-note{display:grid;gap:3px;padding:12px 14px;border-left:2px solid #9b3e33;background:#eeece4;color:#51564e;font-size:12px;line-height:1.55}
    .watchlist-local-note strong{color:#85342c}
    .watchlist-add{max-width:520px;margin:24px 0 0}
    .watchlist-add label{display:block;margin-bottom:7px;font-size:13px;font-weight:650;color:#353a33}
    .watchlist-add-row{display:flex;gap:8px}
    .watchlist-add-row input{flex:1;min-width:0;height:44px;border:1px solid #bab9af;border-radius:2px;background:#fbfaf6;padding:0 11px;font:inherit;font-size:15px;color:#242922}
    .watchlist-add-row input:focus-visible{outline:2px solid #9d3c32;outline-offset:2px}
    .watchlist-add-row button,.watchlist-run{min-height:44px;border:0;border-radius:2px;padding:0 16px;background:#922f27;color:#fff;font:inherit;font-size:13px;font-weight:650;cursor:pointer}
    .watchlist-add-row button:disabled,.watchlist-run:disabled{opacity:.5;cursor:not-allowed}
    .watchlist-add small{display:block;margin-top:7px;color:#6b7068;font-size:11px;line-height:1.55}
    .watchlist-state,.watchlist-message,.watchlist-selection-hint,.watchlist-run-note{font-size:12px;color:#62675f;line-height:1.6}
    .watchlist-message-error,.watchlist-error{color:#873b31}
    .watchlist-error{display:grid;gap:7px;margin-top:18px;padding:13px;border:1px solid #d8b8ae;background:#f8eee9;font-size:13px}
    .watchlist-error button{justify-self:start;border:0;background:none;padding:3px 0;color:#873b31;text-decoration:underline;cursor:pointer}
    .watchlist-empty{margin-top:25px;padding:24px 0;border-top:1px solid #d8d5cc;border-bottom:1px solid #d8d5cc}
    .watchlist-empty strong{font:600 20px Georgia,"Noto Serif TC",serif;color:#292e27}
    .watchlist-empty p{max-width:540px;color:#666b63;font-size:13px;line-height:1.65}
    .watchlist-empty a,.watchlist-research{color:#8f3028;text-decoration:none;font-size:12px}
    .watchlist-empty a:hover,.watchlist-research:hover{text-decoration:underline}
    .watchlist-list-heading{display:flex;align-items:end;justify-content:space-between;gap:12px;margin:25px 0 10px}
    .watchlist-list-heading h2{font:600 20px Georgia,"Noto Serif TC",serif;color:#292e27;margin:0}
    .watchlist-list-heading p{margin:5px 0 0;color:#666b63;font-size:12px}
    .watchlist-list-heading>span{color:#8f3028;font-size:12px;white-space:nowrap}
    .watchlist-items{list-style:none;padding:0;margin:0;border-top:1px solid #d8d5cc}
    .watchlist-items li{display:flex;align-items:center;gap:16px;min-height:58px;padding:9px 0;border-bottom:1px solid #d8d5cc}
    .watchlist-select{display:flex;align-items:center;gap:10px;flex:1;min-width:0;cursor:pointer}
    .watchlist-select input{width:18px;height:18px;accent-color:#922f27}
    .watchlist-select input:focus-visible,.watchlist-remove:focus-visible,.watchlist-add-row button:focus-visible,.watchlist-run:focus-visible{outline:2px solid #9d3c32;outline-offset:2px}
    .watchlist-code{font:600 15px ui-monospace,SFMono-Regular,monospace;color:#292e27}
    .watchlist-added{font-size:11px;color:#72776e}
    .watchlist-remove{min-height:36px;border:1px solid #c8c5bb;border-radius:2px;background:transparent;color:#5f554f;padding:0 10px;font:inherit;font-size:12px;cursor:pointer}
    .watchlist-remove:hover{background:#eeeae2}
    .watchlist-run-note{margin:15px 0 8px}
    .watchlist-run{min-height:46px}
    .watchlist-selection-hint{margin:8px 0}
    @media(max-width:600px){.watchlist-page>h1{font-size:32px}.watchlist-list-heading{align-items:flex-start}.watchlist-items li{gap:9px;flex-wrap:wrap}.watchlist-select{flex-basis:100%}.watchlist-research{margin-left:28px}.watchlist-add-row button{padding:0 12px}}
  `],
})
export class WatchlistPageComponent implements OnInit {
  private readonly store = new WatchlistStore();
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly maxItems = WATCHLIST_MAX_ITEMS;
  items: WatchlistItem[] = [];
  selectedCodes: string[] = [];
  codeInput = '';
  state: PageState = 'loading';
  stateMessage = '';
  message = '';
  messageIsError = false;
  private loadTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.loadTimer = setTimeout(() => this.load(), 0);
    this.destroyRef.onDestroy(() => {
      if (this.loadTimer) clearTimeout(this.loadTimer);
    });
  }

  inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 6);
  }

  add(): void {
    const result = this.store.add(this.codeInput);
    this.applyWriteResult(result);
    if (result.ok) {
      this.codeInput = '';
      this.setMessage('已加入觀察清單。', false);
    }
  }

  remove(item: WatchlistItem): void {
    const result = this.store.remove(item.code);
    this.applyWriteResult(result);
    if (result.ok) {
      this.selectedCodes = this.selectedCodes.filter((code) => code !== item.code);
      this.setMessage('已從觀察清單移除 ' + item.code + '。', false);
    }
  }

  setSelected(code: string, selected: boolean): void {
    if (selected && this.selectedCodes.length < 3 && !this.selectedCodes.includes(code)) {
      this.selectedCodes = [...this.selectedCodes, code];
    } else if (!selected) {
      this.selectedCodes = this.selectedCodes.filter((value) => value !== code);
    }
  }

  runBacktest(): void {
    if (this.selectedCodes.length < 2 || this.selectedCodes.length > 3) return;
    void this.router.navigate(['/backtest'], { queryParams: { symbols: this.selectedCodes.join(',') } });
  }

  trackCode(_index: number, item: WatchlistItem): string {
    return item.code;
  }

  reload(): void {
    this.state = 'loading';
    this.stateMessage = '';
    this.message = '';
    this.load();
  }

  private load(): void {
    const result = this.store.read();
    if (result.ok) {
      this.items = result.items;
      this.state = 'ready';
      this.stateMessage = '';
    } else {
      this.items = [];
      this.state = result.reason === 'malformed' ? 'malformed' : 'unavailable';
      this.stateMessage = result.message;
    }
    this.cdr.markForCheck();
  }

  private applyWriteResult(result: ReturnType<WatchlistStore['add']>): void {
    this.items = result.items;
    if (!result.ok && (result.reason === 'malformed' || result.reason === 'unavailable')) {
      this.state = result.reason;
      this.stateMessage = result.message;
      this.selectedCodes = [];
      return;
    }
    if (!result.ok) this.setMessage(result.message, true);
  }

  private setMessage(value: string, isError: boolean): void {
    this.message = value;
    this.messageIsError = isError;
  }
}
