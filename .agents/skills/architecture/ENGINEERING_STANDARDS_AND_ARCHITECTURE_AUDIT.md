# Engineering Standards and Architecture Audit

Audit date: 2026-10-09. Scope: frontend and backend source/configuration, repository scripts and tests. This is a source-level architecture review; it does not claim production, provider, browser, or load-test verification. Standards sources are recorded in the Angular and Spring Boot Kotlin skill references.

Priority meaning: P1 means address before expanding the affected capability because the design limits correctness, repeatability, or safe evolution; it does not assert a currently observed runtime failure. P2 means planned maintainability or efficiency improvement with no demonstrated user-visible breakage. All findings below are existing conditions, not regressions from this standards update.

## Current architecture

- Frontend: Angular 22 standalone application, Angular Router with hash URLs, RxJS HTTP clients, TypeScript 6. Routes are declared in `frontend/src/main.ts`; page and feature components live under `frontend/src/app/`.
- Backend: Kotlin / Java 25 / Spring Boot 4.1.1, Maven, Spring MVC, Spring Data JPA. Packages are grouped by business feature. External market/catalog providers and selected persistence stores use interfaces.
- Persistence: catalog and replay use JPA repositories/stores; market history currently loads through a provider without a persisted bar store. JPA entities are grouped with their features.
- Browser-only user state: watchlists, research drafts/notes and paper activity are stored locally in the browser. The watchlist explicitly indicates this scope; lack of account synchronization is not a defect unless cross-device persistence is promised.
- Verification: frontend has typecheck and production build scripts, no configured lint or frontend unit-test script. Backend has Maven tests covering API contracts, market client parsing, accounting/replay golden cases, and persistence-facing flows.

## Findings

| Priority | Area | Evidence and assessment | Recommended next action |
|---|---|---|---|
| P1 | Frontend component ownership | `frontend/src/app/app.component.ts` is 700 lines and combines backtest form state, HTTP calls, result mapping, and presentation state. `site-pages.component.ts` groups multiple route page components in 282 lines. This raises change/review coupling; not every multi-page file is itself a defect. | When those flows are next changed, extract cohesive API/data services and split page components by feature, preserving current routes and behavior. Add focused tests alongside the extraction. |
| P1 | Frontend data access duplication | Home/explore behavior in `site-pages.component.ts` and workspace code each own requests/response handling; the explore page uses `any` for response/results. `research-library.component.ts` and `research-journal.component.ts` also use `Record<string, any>`; templates use `$any`. | Define API DTOs and share query services for repeated endpoints. Narrow `unknown` at storage/API boundaries, then remove unsafe types in touched code. |
| P2 | Route loading boundaries | `frontend/src/main.ts` statically imports all page components and eagerly registers them. This is simple and valid, but every page participates in the initial application bundle. | Measure bundle and startup impact; consider `loadComponent`/feature chunks for substantial pages if it reduces initial cost without harming navigation. |
| P1 | Market-provider substitutability | `StockHistoryService` accepts `HistoricalMarketDataProvider`, but `BacktestService` and `RobustnessService` depend directly on `TwseMarketDataClient`. This makes changing vendors and testing those use cases less consistent. | Use the same market-history port where semantics match; add tests with a deterministic fake. Keep provider-specific parsing and retry logic in the adapter. |
| P1 | Historical market data SSOT | `StockHistoryService` obtains history through the provider and returns it; there is no persisted OHLCV/bar store or cache. Backtest metadata explicitly reports `SNAPSHOT_NOT_ARCHIVED`, and stock history marks authorization/completeness limitations. This can repeat upstream fetches and cannot guarantee exact replay of old datasets. | Design an instrument + daily-bar SSOT with source/as-of/fetched timestamps, adjustment policy, completeness/freshness and unique symbol/interval/date keys; separate provider refresh from read API. Confirm data rights and retention before storing or serving beyond permitted use. |
| P2 | Formatting and static analysis | `frontend/package.json` has no lint or unit-test command; `backend/pom.xml` configures tests but no formatter/static-analysis plugin is evident. Several backend service methods are densely formatted, increasing review difficulty. | Select and configure minimal format/lint rules in a separate change; introduce incrementally, starting with changed files to avoid unrelated churn. |
| P2 | Frontend test coverage | No frontend `*.spec.ts` or test runner/configuration was found in the inspected tree. UI interactions therefore rely on build/typecheck and browser evidence rather than automated component regressions. | Add a small Angular test harness and cover search, routing, loading/error, and key form/result behavior before major UI refactors. |

## Existing strengths and constraints

- Backend package organization is feature-based, with clean API DTOs distinct from most persistence records.
- `HistoricalMarketDataProvider`, `LiveQuoteStreamProvider`, `InstrumentCatalogStore`, and replay stores provide real integration/test seams.
- Backtest, accounting, and replay have deterministic golden tests; API contract tests exist.
- JPA is already abstracted behind catalog/replay stores, giving a path to preserve the planned SQLite/other-database flexibility.
- Current market-data constraints are surfaced rather than hidden: delayed/history is not labeled real-time, missing OHLC fields are nullable, and licensing/completeness are marked unknown or blocked.

## Suggested order

1. Establish typed Angular API contracts and extract shared data-access services for endpoints used by multiple pages.
2. Route backtest and robustness through the existing market-data provider port; preserve fake-provider test seams.
3. Design the historical-bars SSOT and freshness/invalidation policy, including source rights and provider-independent schema, before implementing persistent caching.
4. Break up the large backtest page and add frontend tests around extracted behavior; measure before adding lazy route chunks.
5. Add lint/format policy with a baseline and incremental adoption, avoiding a mass reformat in feature changes.

These recommendations are not authorization for a repo-wide rewrite. Each implementation should have its own acceptance criteria and tests.
