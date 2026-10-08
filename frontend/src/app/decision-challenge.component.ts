import { CommonModule } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

interface ReplayObservation { date: string; close: number; }
interface ReplayDecision { date: string; observedClose: number; action: 'WAIT' | 'BUY' | 'SELL'; shares: number; cashAfter: number; sharesAfter: number; }
interface ReplaySession { sessionId: string; challenge: { id: string; title: string; label: string; notice: string }; observation: ReplayObservation | null; state: { cash: number; shares: number; decisionCount: number; complete: boolean }; events: ReplayDecision[]; }
const API_BASE = (window.APP_CONFIG?.apiBaseUrl ?? '').replace(/\/$/, '');

@Component({
  selector: 'app-decision-challenge', standalone: true, imports: [CommonModule, FormsModule],
  template: `
    <section class="page-wrap subpage challenge-page">
      <p class="eyebrow">M4 · 遮蔽未來的決策練習</p>
      <h1 tabindex="-1">先做決定，再看下一天。</h1>
      <p class="subpage-lede">逐步查看目前已知的一筆資料，再選擇觀望、買進或賣出。下一筆資料只會在送出決定後提供。</p>
      <p class="sr-only" role="status" aria-live="polite" aria-atomic="true">{{announcement}}</p>
      <div class="challenge-disclosure"><strong>合成示範資料</strong><span>這是教學流程示範，不是歷史行情、交易建議或績效測試。工作階段只暫存在服務記憶體，重新整理可能無法接續。</span></div>
      <p *ngIf="error" class="notice-error" role="alert">{{error}}</p>
      <div class="challenge-start" *ngIf="!session"><span aria-hidden="true">◷</span><h2>開始一回合決策練習</h2><p>練習期間共有多個觀察點。開始後只會看到當前資料，不會先顯示未來走勢。</p><button class="button-primary" type="button" (click)="start()" [disabled]="busy">{{busy ? '準備練習中…' : '開始練習 →'}}</button></div>
      <ng-container *ngIf="session as current">
        <div class="challenge-topline"><div><span class="challenge-label">{{current.challenge.label}}</span><h2>{{current.challenge.title}}</h2></div><span class="step-count">決策 {{current.state.decisionCount}}<span *ngIf="!current.state.complete"> · 尚有後續觀察</span><span *ngIf="current.state.complete"> · 練習完成</span></span></div>
        <div class="challenge-workspace">
          <section class="observation-card" aria-label="目前可見資料"><p class="card-kicker">目前觀察 · 只含當下資訊</p><div *ngIf="current.observation as observation" class="observation-value"><span>{{observation.date}}</span><strong>{{observation.close | number:'1.2-2'}}</strong><small>合成參考價格</small></div><div *ngIf="!current.observation" class="observation-complete"><strong>沒有下一筆資料</strong><span>你已完成本回合的全部決定。</span></div><dl class="account-glance"><div><dt>虛擬現金</dt><dd>{{current.state.cash | number:'1.0-2'}}</dd></div><div><dt>持有股數</dt><dd>{{current.state.shares}}</dd></div><div><dt>參考資產</dt><dd>{{equity(current) | number:'1.0-2'}}</dd></div></dl></section>
          <section class="decision-card" *ngIf="!current.state.complete"><h3>你現在會怎麼做？</h3><p>決定只會影響這次合成練習。買賣以畫面所示參考價計算，不代表實際成交。</p><label for="challenge-action">本次選擇</label><select id="challenge-action" [(ngModel)]="action"><option value="WAIT">觀望</option><option value="BUY">買進</option><option value="SELL">賣出</option></select><label for="challenge-shares">{{action === 'SELL' ? '賣出股數' : '買進股數'}}</label><input id="challenge-shares" type="number" min="1" step="1" [(ngModel)]="shares" [disabled]="action === 'WAIT'"><p class="decision-hint">{{action === 'WAIT' ? '觀望不會變更現金與持倉。' : action === 'BUY' ? '買進上限會依目前虛擬現金檢查。' : '賣出股數不可超過目前持倉。'}}</p><button class="button-primary" type="button" (click)="decide()" [disabled]="busy">{{busy ? '記錄決定中…' : '記錄決定並前進一天 →'}}</button></section>
          <section class="decision-card challenge-finished" *ngIf="current.state.complete"><h3>這回合完成了</h3><p>回顧每一次決定時你看得到的資料與當下持倉。這份練習不評分，也不代表策略有效。</p><button class="button-outline" type="button" (click)="start()" [disabled]="busy">再練習一次</button></section>
        </div>
        <section class="decision-history" *ngIf="current.events.length"><div class="section-heading"><div><p class="eyebrow">YOUR DECISIONS</p><h2>已做出的決定</h2></div><span>只顯示已揭露日期</span></div><div class="history-list"><article class="history-item" *ngFor="let item of current.events; let i = index"><span class="history-index">{{i + 1}}</span><time>{{item.date}}</time><span class="history-price">{{item.observedClose | number:'1.2-2'}}</span><strong [class.buy-action]="item.action === 'BUY'" [class.sell-action]="item.action === 'SELL'">{{actionName(item.action)}}<small *ngIf="item.shares"> {{item.shares}} 股</small></strong><span class="history-account">現金 {{item.cashAfter | number:'1.0-2'}} · 持有 {{item.sharesAfter}}</span></article></div></section>
        <details class="challenge-rules"><summary>練習規則與限制</summary><p>{{current.challenge.notice}}</p><p>每次合法決定推進一個觀察點；現金不足或賣出超過持倉時，決定會被拒絕且游標不前進。頁面與 API 都不會預先取得未來觀察值。</p></details>
      </ng-container>
    </section>
  `,
})
export class DecisionChallengeComponent {
  private readonly http = inject(HttpClient);
  private readonly changeDetector = inject(ChangeDetectorRef);
  session: ReplaySession | null = null;
  action: 'WAIT' | 'BUY' | 'SELL' = 'WAIT';
  shares = 1;
  busy = false;
  error = '';
  announcement = '';
  async start(): Promise<void> {
    if (!API_BASE) { this.error = '練習 API 尚未設定。'; return; }
    this.error = ''; this.busy = true;
    try { this.session = await firstValueFrom(this.http.post<ReplaySession>(`${API_BASE}/api/replay/sessions`, {})); this.action = 'WAIT'; this.shares = 1; this.announce(this.session); }
    catch (error) { this.error = this.message(error); }
    finally { this.busy = false; this.changeDetector.markForCheck(); }
  }
  async decide(): Promise<void> {
    if (!this.session || this.busy) return;
    this.error = '';
    const body = { action: this.action, shares: this.action === 'WAIT' ? undefined : Number(this.shares) };
    if (this.action !== 'WAIT' && (!Number.isSafeInteger(body.shares) || Number(body.shares) <= 0)) { this.error = '買賣股數必須是大於零的整數。'; return; }
    this.busy = true;
    try { this.session = await firstValueFrom(this.http.post<ReplaySession>(`${API_BASE}/api/replay/sessions/${encodeURIComponent(this.session.sessionId)}/decisions`, body)); this.action = 'WAIT'; this.shares = 1; this.announce(this.session); }
    catch (error) { this.error = this.message(error); }
    finally { this.busy = false; this.changeDetector.markForCheck(); }
  }
  actionName(value: string): string { return value === 'BUY' ? '買進' : value === 'SELL' ? '賣出' : '觀望'; }
  equity(session: ReplaySession): number { const last = session.observation?.close ?? session.events.at(-1)?.observedClose ?? 0; return session.state.cash + session.state.shares * last; }
  private announce(session: ReplaySession): void {
    this.announcement = session.state.complete
      ? `決策練習已完成，共記錄 ${session.state.decisionCount} 次決策。`
      : `第 ${session.state.decisionCount + 1} 步：目前觀察 ${session.observation?.date ?? ''}，合成參考價格 ${session.observation?.close ?? ''}。`;
  }
  private message(error: unknown): string {
    if (error instanceof HttpErrorResponse) return (error.error as { message?: string; error?: string } | null)?.message ?? (error.error as { error?: string } | null)?.error ?? (error.status === 0 ? '目前無法連線至練習 API，請確認服務已啟動。' : '練習無法完成，請檢查輸入後再試。');
    return '練習無法完成，請稍後再試。';
  }
}
