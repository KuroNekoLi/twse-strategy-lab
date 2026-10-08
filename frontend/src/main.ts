import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, withHashLocation, withInMemoryScrolling } from '@angular/router';
import { BacktestWorkspaceComponent } from './app/app.component';
import { AppComponent, ExplorePageComponent, HomePageComponent, MethodsPageComponent, NotFoundPageComponent, StrategiesPageComponent } from './app/site-pages.component';

bootstrapApplication(AppComponent, {
  providers: [
    provideHttpClient(),
    provideRouter([
      { path: '', component: HomePageComponent, title: '研究首頁｜策略實驗室' },
      { path: 'explore', component: ExplorePageComponent, title: '探索標的｜策略實驗室' },
      { path: 'strategies', component: StrategiesPageComponent, title: '策略庫｜策略實驗室' },
      { path: 'backtest', component: BacktestWorkspaceComponent, title: '回測工作台｜策略實驗室' },
      { path: 'methods', component: MethodsPageComponent, title: '方法說明｜策略實驗室' },
      { path: '**', component: NotFoundPageComponent, title: '找不到頁面｜策略實驗室' },
    ], withHashLocation(), withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' })),
  ],
}).catch((error: unknown) => console.error(error));
