# Engineering Standards and Architecture Audit

Audit date: 2026-10-09; implementation follow-up: 2026-10-10. Scope: frontend and backend source/configuration, repository scripts and tests. This is a source-level architecture review; it does not claim production, provider, browser, or load-test verification. Standards sources are recorded in the Angular and Spring Boot Kotlin skill references.

Priority meaning: P1 means address before expanding the affected capability because the design limits correctness, repeatability, or safe evolution; it does not assert a currently observed runtime failure. P2 means planned maintainability or efficiency improvement with no demonstrated user-visible breakage. All findings below are existing conditions, not regressions from this standards update.

## Current architecture

- Frontend: Angular 22 standalone application, Angular Router with hash URLs, RxJS HTTP clients, TypeScript 6. Routes are declared in `frontend/src/main.ts`; page and feature components live under `frontend/src/app/`.
- Backend: Kotlin / Java 25 / Spring Boot 4.1.1, Maven, Spring MVC, Spring Data JPA. Packages are grouped by business feature. External market/catalog providers and selected persistence stores use interfaces.
- Persistence: catalog, replay and historical market data use JPA repositories/stores; market data has persisted daily bars, source metadata and verified requested-range coverage. JPA entities are grouped with their features.
- Browser-only user state: watchlists, research drafts/notes and paper activity are stored locally in the browser. The watchlist explicitly indicates this scope; lack of account synchronization is not a defect unless cross-device persistence is promised.
- Verification: frontend has typecheck and production build scripts, no configured lint or frontend unit-test script. Backend has Maven tests covering API contracts, market client parsing, accounting/replay golden cases, and persistence-facing flows.

## Findings

| Priority | Area | Evidence and assessment | Recommended next action |
|---|---|---|---|
| P1 | Frontend component ownership | `frontend/src/app/app.component.ts` is 700 lines and combines backtest form state, HTTP calls, result mapping, and presentation state. `site-pages.component.ts` groups multiple route page components in 282 lines. This raises change/review coupling; not every multi-page file is itself a defect. | When those flows are next changed, extract cohesive API/data services and split page components by feature, preserving current routes and behavior. Add focused tests alongside the extraction. |
| P1 | Frontend data access duplication | Home/explore behavior in `site-pages.component.ts` and workspace code each own requests/response handling. The explore response is now explicitly typed; `research-library.component.ts` and `research-journal.component.ts` still use `Record<string, any>`, and templates use `$any`. | Define API DTOs and share query services for repeated endpoints. Narrow `unknown` at storage/API boundaries, then remove unsafe types in touched code. |
| P2 | Route loading boundaries | `frontend/src/main.ts` statically imports all page components and eagerly registers them. This is simple and valid, but every page participates in the initial application bundle. | Measure bundle and startup impact; consider `loadComponent`/feature chunks for substantial pages if it reduces initial cost without harming navigation. |
| Resolved 2026-10-10 | Market-provider substitutability | `BacktestService`, `RobustnessService`, and `StockHistoryService` now use `HistoricalMarketDataProvider`; provider parsing and status handling remain in the TWSE adapter. Deterministic provider tests cover consumer behavior. | Keep new history consumers behind the provider port. |
| Resolved 2026-10-10 | Historical market-data SSOT and query backfill | Market history is stored as OHLCV bars with source/fetch metadata. `market_history_coverage` records date ranges successfully checked per symbol. The DB provider serves covered ranges from storage and requests uncovered intervals from the configured historical source, then persists bars and coverage. Historical chart responses still label market calendar coverage unknown and raw prices unadjusted; backtest snapshots remain outside this persistence flow. | Preserve coverage writes in the same transaction as imported bars. Keep provider permission confirmed before importing/displaying; recheck completeness semantics when adding providers or adjustment modes. |
| P2 | Formatting and static analysis | `frontend/package.json` has no lint or unit-test command; `backend/pom.xml` configures tests but no formatter/static-analysis plugin is evident. Several backend service methods are densely formatted, increasing review difficulty. | Select and configure minimal format/lint rules in a separate change; introduce incrementally, starting with changed files to avoid unrelated churn. |
| P2 | Frontend test coverage | No frontend `*.spec.ts` or test runner/configuration was found in the inspected tree. UI interactions therefore rely on build/typecheck and browser evidence rather than automated component regressions. | Add a small Angular test harness and cover search, routing, loading/error, and key form/result behavior before major UI refactors. |

## Existing strengths and constraints

- Backend package organization is feature-based, with clean API DTOs distinct from most persistence records.
- `HistoricalMarketDataProvider`, `LiveQuoteStreamProvider`, `InstrumentCatalogStore`, and replay stores provide real integration/test seams.
- Backtest, accounting, and replay have deterministic golden tests; API contract tests exist.
- JPA is already abstracted behind catalog/replay stores, giving a path to preserve the planned SQLite/other-database flexibility.
- Current market-data constraints are surfaced rather than hidden: delayed/history is not labeled real-time, missing OHLC fields are nullable, and licensing/completeness are marked unknown or blocked.

## Suggested order

1. Extract shared data-access services for endpoints used by multiple pages; the Explore catalog response is now typed, while other research pages retain unsafe record types.
2. Route backtest and robustness through the existing market-data provider port; completed 2026-10-10, with deterministic fake-provider tests.
3. Add a provider-independent expiry/freshness policy for recent ranges and distributed backfill coordination if the service scales beyond one instance; retain the current source and date-range limits.
4. Break up the large backtest page and add frontend tests around extracted behavior; measure before adding lazy route chunks.
5. Add lint/format policy with a baseline and incremental adoption, avoiding a mass reformat in feature changes.

These recommendations are not authorization for a repo-wide rewrite. Each implementation should have its own acceptance criteria and tests.

## 2026-10-10 implementation follow-up

- Resolved the provider-boundary finding before adding data-availability feedback: backtest and robustness now depend on `HistoricalMarketDataProvider`, not the TWSE client implementation.
- The Explore catalog consumer now narrows typed responses and distinguishes supported, unsupported, and unknown states.
- Remaining architecture work includes backfill expiry/distributed coordination, broader frontend data-service extraction, and focused UI tests. Historical-data persistence does not establish market-calendar completeness, adjusted-price accuracy, live-provider health, or production behavior.
- Automated verification for this slice: backend Maven tests (97) and frontend typecheck/production build. Browser evidence is recorded separately under the product opportunity note when available.
