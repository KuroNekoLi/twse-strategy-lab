import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

type Side = 'BUY' | 'SELL';
type OrderStatus = 'CREATED' | 'SUBMITTED' | 'ACCEPTED' | 'FILLED' | 'CANCELLED' | 'REJECTED';
type LedgerEvent =
  | { kind: 'DEPOSIT'; eventId: string; occurredAt: string; effectiveDate: string; amountCents: string }
  | { kind: 'ORDER'; eventId: string; occurredAt: string; effectiveDate: string; orderId: string; type: 'CREATED' | 'SUBMITTED' | 'ACCEPTED' | 'FILLED' | 'CANCELLED' | 'REJECTED'; symbol: string; side: Side; quantity: number; reason?: string }
  | { kind: 'FILL'; eventId: string; occurredAt: string; effectiveDate: string; orderId: string; symbol: string; side: Side; quantity: number; priceCents: string; commissionBps: number; sellTaxBps: number };
type PersistedLedger = { schemaVersion: 1; events: LedgerEvent[] };
type Position = { symbol: string; quantity: number; costCents: bigint };
type OrderView = { id: string; symbol: string; side: Side; quantity: number; status: OrderStatus; createdAt: string; fillPrice?: string; reason?: string };
type Projection = { cashCents: bigint; deposited: boolean; positions: Map<string, Position>; orders: Map<string, OrderView> };

const STORAGE_KEY = 'twse-strategy-lab.paper-ledger';
const MAX_EVENTS = 10000;
const zeroProjection = (): Projection => ({ cashCents: 0n, deposited: false, positions: new Map(), orders: new Map() });
const isoDate = (value: string): boolean => /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
const isRecord = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value);
const hasExactKeys = (value: Record<string, unknown>, keys: string[]): boolean => Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
const money = (cents: bigint): string => `${cents < 0n ? '-' : ''}$${(cents < 0n ? -cents : cents) / 100n}.${String((cents < 0n ? -cents : cents) % 100n).padStart(2, '0')}`;
const dollars = (cents: bigint): string => `${(cents < 0n ? '-' : '')}${(cents < 0n ? -cents : cents) / 100n}.${String((cents < 0n ? -cents : cents) % 100n).padStart(2, '0')}`;
const parseCents = (input: string): bigint | null => {
  const value = input.trim();
  const match = /^(?:0|[1-9]\d{0,11})(?:\.(\d{1,2}))?$/.exec(value);
  if (!match) return null;
  const [whole, fraction = ''] = value.split('.');
  return BigInt(whole) * 100n + BigInt((fraction + '00').slice(0, 2));
};
const feeCents = (gross: bigint, bps: number): bigint => (gross * BigInt(bps) + 5000n) / 10000n;

@Component({
  selector: 'app-paper-trading',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="paper-shell">
      <header class="hero">
        <p class="eyebrow">M3 · LOCAL LEARNING SANDBOX</p>
        <h1 tabindex="-1">紙上交易練習</h1>
        <p class="lede">用本機虛擬帳戶練習建立訂單、檢查資金與重播帳務事件。</p>
      </header>
      <aside class="notice" aria-label="重要限制"><strong>這不是實際交易</strong><p>只在此瀏覽器保存資料，不連接 TWSE 行情或券商。接受訂單不代表市場接受；成交只按你輸入的參考價格記帳，不模擬委託簿、排隊、部分成交、交割、券商費率、稅務或公司行動。清除瀏覽器資料會刪除此帳本。請勿輸入個人或金融帳戶資料。</p></aside>

      <div *ngIf="loadError" class="error-banner" role="alert"><strong>帳本目前以唯讀方式保留。</strong><span>{{loadError}}</span><p>原始瀏覽器資料未被改寫。請先匯出或備份瀏覽器資料，再處理格式問題。</p></div>
      <div *ngIf="storageError && !loadError" class="error-banner" role="alert">{{storageError}} 本次操作未寫入帳本。</div>
      <p *ngIf="notice" class="status-banner" role="status" aria-live="polite">{{notice}}</p>

      <section class="summary-grid" aria-label="帳戶摘要">
        <article class="summary-card"><span>可用現金</span><strong>{{money(projection.cashCents)}}</strong><small>以已記錄事件重播</small></article>
        <article class="summary-card"><span>持有標的</span><strong>{{projection.positions.size}}</strong><small>不包含未實現損益</small></article>
        <article class="summary-card"><span>待處理訂單</span><strong>{{pendingCount}}</strong><small>建立、送出或接受狀態</small></article>
      </section>

      <div class="workspace-grid">
        <section class="panel">
          <div class="panel-heading"><div><p class="eyebrow">ACCOUNT</p><h2>建立虛擬帳戶</h2></div></div>
          <p class="helper" *ngIf="!initialized">先存入初始虛擬資金，才可建立訂單。</p>
          <form (ngSubmit)="deposit()" class="form-grid" *ngIf="!initialized">
            <label>初始虛擬資金（元）<input name="depositAmount" [(ngModel)]="depositAmount" inputmode="decimal" autocomplete="off" placeholder="例如 100000" [disabled]="readOnly"></label>
            <button type="submit" [disabled]="readOnly">建立帳戶並存入</button>
          </form>
          <p *ngIf="initialized" class="success-line" role="status">帳戶已建立 · 本機事件 {{events.length}} 筆</p>
          <div class="positions" *ngIf="projection.positions.size">
            <h3>持倉股數</h3><div class="position-row" *ngFor="let item of positionList"><strong>{{item.symbol}}</strong><span>{{item.quantity | number}} 股</span><small>事件成本 {{money(item.costCents)}}</small></div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-heading"><div><p class="eyebrow">ORDER TICKET</p><h2>建立練習訂單</h2></div></div>
          <form (ngSubmit)="createOrder()" class="form-grid two-col">
            <label>標的代碼<input name="symbol" [(ngModel)]="symbol" inputmode="numeric" autocomplete="off" maxlength="12" placeholder="例如 0050" [disabled]="!canTransact"></label>
            <label>買賣方向<select name="side" [(ngModel)]="side" [disabled]="!canTransact"><option value="BUY">買進</option><option value="SELL">賣出</option></select></label>
            <label>股數<input name="quantity" [(ngModel)]="quantity" inputmode="numeric" autocomplete="off" placeholder="整數股" [disabled]="!canTransact"></label>
            <label>模擬手續費（基點）<input name="commissionBps" [(ngModel)]="commissionBps" inputmode="numeric" autocomplete="off" [disabled]="!canTransact"></label>
            <label *ngIf="side === 'SELL'">模擬賣出稅（基點）<input name="sellTaxBps" [(ngModel)]="sellTaxBps" inputmode="numeric" autocomplete="off" [disabled]="!canTransact"></label>
            <p class="field-note">1 基點 = 0.01%。預設為 0；這些是練習參數，不代表官方或券商費率。</p>
            <button type="submit" [disabled]="!canTransact">建立訂單</button>
          </form>
          <p class="helper">建立後可依序送出、接受，再輸入參考價模擬全額成交；也可取消或拒絕。建立與接受本身不凍結資金。</p>
        </section>
      </div>

      <section class="panel history-panel">
        <div class="panel-heading"><div><p class="eyebrow">ORDER LIFECYCLE</p><h2>訂單與操作</h2></div><span class="count">{{orderList.length}} 筆</span></div>
        <p *ngIf="!orderList.length" class="empty">建立第一筆練習訂單後，操作按鈕會顯示在這裡。</p>
        <article class="order-row" *ngFor="let order of orderList">
          <div class="order-main"><div><strong>{{order.symbol}} · {{order.side === 'BUY' ? '買進' : '賣出'}} {{order.quantity | number}} 股</strong><span>建立 {{order.createdAt | date:'yyyy/MM/dd HH:mm:ss'}}</span></div><span class="badge" [attr.data-status]="order.status">{{statusLabel(order.status)}}</span></div>
          <p class="order-detail" *ngIf="order.fillPrice">參考成交價 {{order.fillPrice}} 元 · 已按輸入價格全額記帳</p>
          <p class="order-detail rejected" *ngIf="order.reason">{{order.reason}}</p>
          <div class="actions" *ngIf="!isTerminal(order.status)">
            <button type="button" class="secondary" *ngIf="order.status === 'CREATED'" (click)="transition(order,'SUBMITTED')" [disabled]="readOnly">送出練習訂單</button>
            <button type="button" *ngIf="order.status === 'SUBMITTED'" (click)="transition(order,'ACCEPTED')" [disabled]="readOnly">接受（教學狀態）</button>
            <ng-container *ngIf="order.status === 'ACCEPTED'"><label class="price-field">輸入參考成交價（元）<input [name]="'price-'+order.id" [(ngModel)]="fillPrices[order.id]" inputmode="decimal" autocomplete="off" placeholder="例如 120.50" [disabled]="readOnly"></label><button type="button" (click)="fill(order)" [disabled]="readOnly">按此價格全額成交</button></ng-container>
            <button type="button" class="quiet" *ngIf="order.status === 'SUBMITTED' || order.status === 'ACCEPTED'" (click)="transition(order,'CANCELLED')" [disabled]="readOnly">取消</button>
            <button type="button" class="quiet" *ngIf="order.status === 'SUBMITTED'" (click)="reject(order)" [disabled]="readOnly">拒絕</button>
          </div>
        </article>
      </section>

      <section class="panel history-panel">
        <div class="panel-heading"><div><p class="eyebrow">APPEND-ONLY LEDGER</p><h2>帳務事件</h2></div><span class="count">{{events.length}} 筆</span></div>
        <p class="helper">帳戶餘額與持倉僅由以下事件依序重建；事件不提供編輯或刪除控制。</p>
        <ol class="event-list"><li *ngFor="let event of reversedEvents"><time>{{event.occurredAt | date:'yyyy/MM/dd HH:mm:ss'}}</time><strong>{{eventLabel(event)}}</strong><code>{{event.eventId}}</code></li></ol>
        <p *ngIf="!events.length" class="empty">尚無帳務事件。</p>
      </section>
      <footer>本機資料版本 1 · 只在目前瀏覽器 · 無登入、同步、行情連線或匯出功能</footer>
    </section>
  `,
  styles: [`
    :host{display:block;color:#e8edf4;font:inherit}.paper-shell{max-width:none;min-height:calc(100vh - 132px);box-sizing:border-box;margin:0;padding:42px max(24px,calc((100vw - 1120px)/2)) 64px;background:#142638;color:#e8edf4}.hero{margin-bottom:24px}.eyebrow{margin:0 0 9px;color:#74cbb7;font-size:.72rem;font-weight:800;letter-spacing:.13em}.hero h1{margin:0;font-size:clamp(2rem,5vw,3.35rem);letter-spacing:-.045em;color:#f3f7fa}.lede{color:#c1cbd6;font-size:1.05rem}.notice,.error-banner,.status-banner{border:1px solid #49635f;background:#142522;border-radius:14px;padding:17px 20px;margin:20px 0 24px}.notice strong{color:#9ce2d2}.notice p,.error-banner p{margin:7px 0 0;color:#c1cbd6;line-height:1.65}.error-banner{border-color:#a85555;background:#321d21;display:grid;gap:6px}.error-banner p{margin:0}.status-banner{border-color:#356c5e;color:#b8f1df}.summary-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;margin-bottom:20px}.summary-card,.panel{background:#151d27;border:1px solid #293544;border-radius:16px}.summary-card{padding:18px 20px;display:grid;gap:5px}.summary-card span,.summary-card small{color:#a9b4c2}.summary-card strong{font-size:1.65rem;font-variant-numeric:tabular-nums}.workspace-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.panel{padding:22px;margin-bottom:16px}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:16px}.panel-heading h2{margin:0;font-size:1.3rem}.panel-heading .eyebrow{margin-bottom:5px}.helper,.empty{color:#aebaca;line-height:1.55}.form-grid{display:grid;gap:13px}.form-grid.two-col{grid-template-columns:1fr 1fr}.form-grid label,.price-field{display:grid;gap:6px;color:#c9d2dc;font-size:.88rem}.form-grid input,.form-grid select,.price-field input{width:100%;min-height:44px;border:1px solid #425064;border-radius:9px;background:#0f151e;color:#f4f6f8;padding:9px 11px;font:inherit;box-sizing:border-box}.form-grid input:focus,.form-grid select:focus,.price-field input:focus,button:focus-visible{outline:3px solid #75d8c4;outline-offset:2px}.field-note{grid-column:1/-1;margin:0;color:#aebaca;font-size:.83rem;line-height:1.45}button{min-height:42px;border:0;border-radius:9px;background:#67d2b8;color:#10221f;padding:9px 14px;font:inherit;font-weight:750;cursor:pointer}button:disabled{opacity:.45;cursor:not-allowed}.form-grid>button{justify-self:start}.success-line{color:#9ce2d2}.positions{margin-top:23px}.positions h3{font-size:1rem}.position-row{display:grid;grid-template-columns:1fr auto;gap:4px;padding:11px 0;border-top:1px solid #303b49}.position-row small{grid-column:1/-1;color:#aebaca}.count{color:#aebaca;font-size:.86rem}.order-row{padding:15px 0;border-top:1px solid #303b49}.order-main{display:flex;align-items:center;justify-content:space-between;gap:12px}.order-main>div{display:grid;gap:5px}.order-main>div span,.order-detail{font-size:.86rem;color:#aebaca}.badge{border-radius:99px;padding:5px 10px;background:#283646;color:#d5e2ee;font-size:.77rem;white-space:nowrap}.badge[data-status="FILLED"]{background:#214b3d;color:#b6f0d9}.badge[data-status="REJECTED"],.badge[data-status="CANCELLED"]{background:#493034;color:#f0c2c4}.actions{display:flex;align-items:end;gap:9px;flex-wrap:wrap;margin-top:12px}.actions button{font-size:.86rem}.secondary{background:#304559;color:#e1edf7}.quiet{background:transparent;color:#c2ccd7;border:1px solid #485463}.price-field{min-width:180px;flex:1}.order-detail.rejected{color:#ffc0bd}.event-list{list-style:none;padding:0;margin:12px 0 0}.event-list li{display:grid;grid-template-columns:minmax(130px,auto) 1fr;gap:4px 12px;padding:11px 0;border-top:1px solid #303b49}.event-list time{color:#aebaca;font-size:.8rem}.event-list strong{font-size:.88rem}.event-list code{grid-column:2;color:#8997a7;font-size:.72rem;overflow-wrap:anywhere}.paper-shell footer{color:#929eac;font-size:.8rem;padding-top:4px}@media(max-width:760px){.paper-shell{padding:28px 16px 48px}.summary-grid{grid-template-columns:1fr}.summary-card{grid-template-columns:1fr auto;align-items:center}.summary-card small{grid-column:1/-1}.workspace-grid{grid-template-columns:1fr}.form-grid.two-col{grid-template-columns:1fr}.field-note{grid-column:auto}.panel{padding:18px}.order-main{align-items:flex-start}.event-list li{grid-template-columns:1fr}.event-list code{grid-column:1}.actions>*{width:100%}.actions button{min-height:44px}}
  `]
})
export class PaperTradingComponent {
  events: LedgerEvent[] = [];
  projection: Projection = zeroProjection();
  readOnly = false;
  loadError = '';
  storageError = '';
  notice = '';
  depositAmount = '100000';
  symbol = '';
  side: Side = 'BUY';
  quantity = '';
  commissionBps = '0';
  sellTaxBps = '0';
  fillPrices: Record<string, string> = {};

  constructor() { this.restore(); }

  private restore(): void {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw === null) return;
      const parsed: unknown = JSON.parse(raw);
      if (!isRecord(parsed) || parsed['schemaVersion'] !== 1 || !Array.isArray(parsed['events']) || !hasExactKeys(parsed, ['schemaVersion', 'events'])) throw new Error('帳本格式無效或版本不支援。');
      const events = parsed['events'] as unknown[];
      if (events.length > MAX_EVENTS || !events.every(event => this.validEvent(event))) throw new Error('帳本包含格式錯誤或不支援的事件。');
      const replay = this.replay(events as LedgerEvent[]);
      this.events = events as LedgerEvent[];
      this.projection = replay;
    } catch (error) {
      this.readOnly = true;
      this.loadError = error instanceof Error ? error.message : '無法安全讀取本機帳本。';
    }
  }

  private validEvent(input: unknown): input is LedgerEvent {
    if (!isRecord(input) || typeof input['kind'] !== 'string' || typeof input['eventId'] !== 'string' || !/^[\w-]{8,100}$/.test(input['eventId']) || typeof input['occurredAt'] !== 'string' || Number.isNaN(Date.parse(input['occurredAt'])) || typeof input['effectiveDate'] !== 'string' || !isoDate(input['effectiveDate'])) return false;
    if (input['kind'] === 'DEPOSIT') return hasExactKeys(input, ['kind','eventId','occurredAt','effectiveDate','amountCents']) && typeof input['amountCents'] === 'string' && /^(?:[1-9]\d{0,13})$/.test(input['amountCents']);
    if (input['kind'] === 'ORDER') return hasExactKeys(input, input['reason'] === undefined ? ['kind','eventId','occurredAt','effectiveDate','orderId','type','symbol','side','quantity'] : ['kind','eventId','occurredAt','effectiveDate','orderId','type','symbol','side','quantity','reason']) && typeof input['orderId'] === 'string' && /^[\w-]{8,100}$/.test(input['orderId']) && ['CREATED','SUBMITTED','ACCEPTED','FILLED','CANCELLED','REJECTED'].includes(String(input['type'])) && typeof input['symbol'] === 'string' && /^[A-Za-z0-9.-]{1,12}$/.test(input['symbol']) && (input['side'] === 'BUY' || input['side'] === 'SELL') && Number.isSafeInteger(input['quantity']) && Number(input['quantity']) > 0 && (input['reason'] === undefined || typeof input['reason'] === 'string');
    if (input['kind'] === 'FILL') return hasExactKeys(input, ['kind','eventId','occurredAt','effectiveDate','orderId','symbol','side','quantity','priceCents','commissionBps','sellTaxBps']) && typeof input['orderId'] === 'string' && /^[\w-]{8,100}$/.test(input['orderId']) && typeof input['symbol'] === 'string' && /^[A-Za-z0-9.-]{1,12}$/.test(input['symbol']) && (input['side'] === 'BUY' || input['side'] === 'SELL') && Number.isSafeInteger(input['quantity']) && Number(input['quantity']) > 0 && typeof input['priceCents'] === 'string' && /^(?:[1-9]\d{0,11})$/.test(input['priceCents']) && Number.isSafeInteger(input['commissionBps']) && Number(input['commissionBps']) >= 0 && Number(input['commissionBps']) <= 10000 && Number.isSafeInteger(input['sellTaxBps']) && Number(input['sellTaxBps']) >= 0 && Number(input['sellTaxBps']) <= 10000;
    return false;
  }

  private replay(events: LedgerEvent[]): Projection {
    const p = zeroProjection();
    const seen = new Set<string>();
    let lastOccurredAt = '';
    let lastEffectiveDate = '';
    for (const event of events) {
      if (seen.has(event.eventId)) throw new Error('事件 ID 重複，無法安全重播。');
      seen.add(event.eventId);
      if (event.occurredAt < lastOccurredAt || event.effectiveDate < lastEffectiveDate) throw new Error('事件時間順序不合法，無法安全重播。');
      lastOccurredAt = event.occurredAt;
      lastEffectiveDate = event.effectiveDate;
      if (event.kind === 'DEPOSIT') {
        if (p.orders.size || p.cashCents !== 0n || p.deposited) throw new Error('初始入金必須是唯一且第一筆帳務事件。');
        p.deposited = true;
        p.cashCents = BigInt(event.amountCents);
      } else if (event.kind === 'ORDER') {
        const order = p.orders.get(event.orderId);
        if (event.type === 'CREATED') {
          if (order || !p.deposited) throw new Error('帳戶未建立，或訂單 ID 重複。');
          p.orders.set(event.orderId, { id: event.orderId, symbol: event.symbol, side: event.side, quantity: event.quantity, status: 'CREATED', createdAt: event.occurredAt });
        } else {
          if (!order || order.symbol !== event.symbol || order.side !== event.side || order.quantity !== event.quantity || !this.allowedTransition(order.status, event.type)) throw new Error('訂單狀態轉移或訂單內容不合法。');
          order.status = event.type;
          if (event.reason) order.reason = event.reason;
        }
      } else {
        const order = p.orders.get(event.orderId);
        if (!order || order.status !== 'ACCEPTED' || order.symbol !== event.symbol || order.side !== event.side || order.quantity !== event.quantity) throw new Error('成交事件不符合已接受訂單。');
        const gross = BigInt(event.priceCents) * BigInt(event.quantity);
        const commission = feeCents(gross, event.commissionBps);
        const tax = event.side === 'SELL' ? feeCents(gross, event.sellTaxBps) : 0n;
        if (event.side === 'BUY') {
          if (p.cashCents < gross + commission) throw new Error('買進成交資金不足。');
          p.cashCents -= gross + commission;
          const position = p.positions.get(event.symbol) ?? { symbol: event.symbol, quantity: 0, costCents: 0n };
          if (!Number.isSafeInteger(position.quantity + event.quantity)) throw new Error('持倉股數超出安全範圍。');
          position.quantity += event.quantity;
          position.costCents += gross + commission;
          p.positions.set(event.symbol, position);
        } else {
          const position = p.positions.get(event.symbol);
          if (!position || position.quantity < event.quantity) throw new Error('賣出成交股數不足。');
          if (commission + tax > gross) throw new Error('手續費與稅額超過成交總額。');
          p.cashCents += gross - commission - tax;
          position.quantity -= event.quantity;
          position.costCents = position.quantity === 0 ? 0n : (position.costCents * BigInt(position.quantity) + BigInt(position.quantity + event.quantity) / 2n) / BigInt(position.quantity + event.quantity);
          if (!position.quantity) p.positions.delete(event.symbol);
        }
        order.status = 'FILLED';
        order.fillPrice = dollars(BigInt(event.priceCents));
      }
    }
    return p;
  }

  private allowedTransition(from: OrderStatus, to: OrderStatus): boolean {
    return (from === 'CREATED' && to === 'SUBMITTED') || (from === 'SUBMITTED' && ['ACCEPTED','REJECTED','CANCELLED'].includes(to)) || (from === 'ACCEPTED' && ['FILLED','CANCELLED'].includes(to));
  }

  private commit(next: LedgerEvent[]): boolean {
    if (this.readOnly) return false;
    if (next.length > MAX_EVENTS) { this.storageError = '事件數已達本機保存上限。'; return false; }
    try {
      const projection = this.replay(next);
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ schemaVersion: 1, events: next } satisfies PersistedLedger));
      this.events = next;
      this.projection = projection;
      this.storageError = '';
      this.notice = '操作已記錄於此瀏覽器，帳戶狀態由事件重播。';
      return true;
    } catch (error) {
      this.storageError = error instanceof Error ? error.message : '無法儲存本機事件。';
      return false;
    }
  }

  deposit(): void {
    const cents = parseCents(this.depositAmount);
    if (this.initialized) return this.fail('帳戶已建立，不能重複建立初始入金。');
    if (cents === null || cents <= 0n) return this.fail('請輸入大於 0 且最多兩位小數的初始資金。');
    this.commit([...this.events, { kind:'DEPOSIT', eventId:this.id(), occurredAt:new Date().toISOString(), effectiveDate:this.today(), amountCents:String(cents) }]);
  }

  createOrder(): void {
    const code = this.symbol.trim().toUpperCase();
    const qty = Number(this.quantity);
    if (!this.initialized) return this.fail('請先建立虛擬帳戶。');
    if (!/^[A-Z0-9.-]{1,12}$/.test(code)) return this.fail('標的代碼只能包含英數字、點或連字號，長度 1–12。');
    if (!/^\d+$/.test(this.quantity) || !Number.isSafeInteger(qty) || qty <= 0) return this.fail('股數必須是正整數且在安全範圍內。');
    if (!this.validBps(this.commissionBps) || (this.side === 'SELL' && !this.validBps(this.sellTaxBps))) return this.fail('費率必須是 0–10000 的整數基點。');
    if (this.side === 'SELL' && (this.projection.positions.get(code)?.quantity ?? 0) < qty) return this.fail('目前可用持股不足；本沙盒不支援融券或預留股數。');
    const orderId = this.id();
    const event: LedgerEvent = { kind:'ORDER', eventId:this.id(), orderId, type:'CREATED', symbol:code, side:this.side, quantity:qty, occurredAt:new Date().toISOString(), effectiveDate:this.today() };
    if (this.commit([...this.events, event])) { this.symbol=''; this.quantity=''; }
  }

  transition(order: OrderView, type: 'SUBMITTED' | 'ACCEPTED' | 'CANCELLED'): void {
    this.appendOrderEvent(order, type);
  }

  reject(order: OrderView): void { this.appendOrderEvent(order, 'REJECTED', '使用者在本機教學流程中拒絕'); }

  fill(order: OrderView): void {
    const cents = parseCents(this.fillPrices[order.id] ?? '');
    if (cents === null || cents <= 0n) return this.fail('參考成交價必須大於 0，且最多兩位小數。');
    const commission = Number(this.commissionBps);
    const tax = order.side === 'SELL' ? Number(this.sellTaxBps) : 0;
    if (!this.validBps(this.commissionBps) || !this.validBps(String(tax))) return this.fail('費率必須是 0–10000 的整數基點。');
    const fill: LedgerEvent = { kind:'FILL', eventId:this.id(), orderId:order.id, symbol:order.symbol, side:order.side, quantity:order.quantity, priceCents:String(cents), commissionBps:commission, sellTaxBps:tax, occurredAt:new Date().toISOString(), effectiveDate:this.today() };
    // A single immutable fill fact also transitions the replayed order to FILLED.
    const acceptance = [...this.events].reverse().find(event => event.kind === 'ORDER' && event.orderId === order.id && event.type === 'ACCEPTED');
    if (!acceptance) return this.fail('只有已接受的訂單可以模擬成交。');
    if (this.commit([...this.events, fill])) delete this.fillPrices[order.id];
  }

  private appendOrderEvent(order: OrderView, type: 'SUBMITTED' | 'ACCEPTED' | 'CANCELLED' | 'REJECTED', reason?: string): void {
    const event: LedgerEvent = { kind:'ORDER', eventId:this.id(), orderId:order.id, type, symbol:order.symbol, side:order.side, quantity:order.quantity, occurredAt:new Date().toISOString(), effectiveDate:this.today(), ...(reason ? {reason} : {}) };
    this.commit([...this.events, event]);
  }

  get initialized(): boolean { return this.events.some(event => event.kind === 'DEPOSIT'); }
  get canTransact(): boolean { return this.initialized && !this.readOnly; }
  get positionList(): Position[] { return [...this.projection.positions.values()]; }
  get orderList(): OrderView[] { return [...this.projection.orders.values()].reverse(); }
  get reversedEvents(): LedgerEvent[] { return [...this.events].reverse(); }
  get pendingCount(): number { return [...this.projection.orders.values()].filter(order => !this.isTerminal(order.status)).length; }
  isTerminal(status: OrderStatus): boolean { return ['FILLED','CANCELLED','REJECTED'].includes(status); }
  money = money;
  statusLabel(status: OrderStatus): string { return ({CREATED:'已建立',SUBMITTED:'已送出',ACCEPTED:'教學接受',FILLED:'模擬成交',CANCELLED:'已取消',REJECTED:'已拒絕'})[status]; }
  eventLabel(event: LedgerEvent): string {
    if (event.kind === 'DEPOSIT') return `虛擬初始入金 ${money(BigInt(event.amountCents))}`;
    if (event.kind === 'FILL') return `${event.symbol} ${event.side === 'BUY' ? '買進' : '賣出'} ${event.quantity} 股 · 使用者參考價 ${dollars(BigInt(event.priceCents))} 元`;
    return `${event.orderId.slice(0,8)} · ${event.symbol} ${event.side === 'BUY' ? '買進' : '賣出'} ${event.quantity} 股 · ${this.statusLabel(event.type)}`;
  }
  private validBps(value: string): boolean { return /^\d{1,5}$/.test(value) && Number(value) <= 10000; }
  private fail(message: string): void { this.storageError = message; this.notice = ''; }
  private today(): string { return new Date().toISOString().slice(0,10); }
  private id(): string { return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2,12)}`; }
}
