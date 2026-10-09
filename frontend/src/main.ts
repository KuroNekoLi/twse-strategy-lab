import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, withHashLocation, withInMemoryScrolling } from '@angular/router';
import { BacktestWorkspaceComponent } from './app/app.component';
import { AppComponent, ExplorePageComponent, HomePageComponent, MethodsPageComponent, NotFoundPageComponent, StrategiesPageComponent } from './app/site-pages.component';
import { ResearchLibraryComponent } from './app/research-library.component';
import { PaperTradingComponent } from './app/paper-trading.component';
import { DecisionChallengeComponent } from './app/decision-challenge.component';
import { RobustnessLabComponent } from './app/robustness-lab.component';
import { ResearchJournalComponent } from './app/research-journal.component';
import { StockResearchPageComponent } from './app/stock-research.component';
import { WatchlistPageComponent } from './app/watchlist.component';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(),
    provideRouter([
      { path: '', component: HomePageComponent, title: '研究首頁｜策略實驗室' },
      { path: 'explore', component: ExplorePageComponent, title: '探索標的｜策略實驗室' },
      { path: 'stocks/:symbol', component: StockResearchPageComponent, title: '個股研究｜策略實驗室' },
      { path: 'watchlist', component: WatchlistPageComponent, title: '觀察清單｜策略實驗室' },
      { path: 'strategies', component: StrategiesPageComponent, title: '策略庫｜策略實驗室' },
      { path: 'backtest', component: BacktestWorkspaceComponent, title: '回測工作台｜策略實驗室' },
      { path: 'my-research', component: ResearchLibraryComponent, title: '我的研究｜策略實驗室' },
      { path: 'journal', component: ResearchJournalComponent, title: '研究筆記｜策略實驗室' },
      { path: 'paper', component: PaperTradingComponent, title: '模擬交易｜策略實驗室' },
      { path: 'decision-practice', component: DecisionChallengeComponent, title: '決策練習｜策略實驗室' },
      { path: 'robustness', component: RobustnessLabComponent, title: '穩健性實驗室｜策略實驗室' },
      { path: 'methods', component: MethodsPageComponent, title: '方法說明｜策略實驗室' },
      { path: '**', component: NotFoundPageComponent, title: '找不到頁面｜策略實驗室' },
    ], withHashLocation(), withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })),
  ],
}).catch((error: unknown) => console.error(error));
