# M1 Browser Runtime Report

**Status: PASS (39/39 automated checks; M1 result/volatility evidence observed).**

## Runtime and build

- Label: `BROWSER_RUNTIME`, `AUTOMATED_TEST`.
- Runtime identity: `/root/m1_browser` (`M1-BROWSER`).
- Command: `QA_MILESTONE=M1 QA_RUNTIME_ID=/root/m1_browser node docs/quality/scripts/m0-browser.cjs` from the repository root. The frontend was rebuilt immediately before this run with `npm run build`.
- Runtime: Google Chrome 154.0.8037.98 (`chrome` channel), headless, macOS Darwin 25.6.0 arm64; Node v22.22.3; Amazon Corretto Java 17.0.15.
- Target: local Angular production build served at `http://127.0.0.1:14201`, actual Spring Boot JAR on `127.0.0.1:18081`, and a synthetic-only TWSE-shaped fixture service on `127.0.0.1:19081`. No live market-data requests were made.
- Build identity: JAR SHA-256 `937a413eca47d0002afca3ef7d278a6f7f8b1b34bb56a8a6e83919a53358b2aa`; frontend `main-GORVYZH4.js` SHA-256 `794772e747c02c4b55d51b87ebfcb8e7c2e229fc967e63d0a462ec0d099689bc`; stylesheet `styles-YI7KXW4V.css` SHA-256 `c6ddcbbdce53bd78f44fdd750bbb3d0e0a33383387685c0bfc3d5d8f419c6d96`; JAR hash was unchanged during the run.

## Results

The suite reported **39 PASS, 0 FAIL** across three isolated browser contexts:

| Context | Web viewport | Evidence |
|---|---:|---|
| `desktop` | 1440 × 1000 | [result screenshot](evidence/m1-browser/desktop-result-details.png), [Playwright trace](evidence/m1-browser/desktop-trace.zip) |
| `tablet` | 768 × 1024 | [result screenshot](evidence/m1-browser/tablet-result-details.png), [Playwright trace](evidence/m1-browser/tablet-trace.zip) |
| `mobile-web` | 390 × 844 | [result screenshot](evidence/m1-browser/mobile-web-result-details.png), [Playwright trace](evidence/m1-browser/mobile-web-trace.zip) |

Each context rendered a successful run with three result rows: the selected strategy, monthly dollar-cost averaging, and buy-and-hold. Desktop additionally exercised all five strategies, two-symbol comparison (six result rows), cost override, validation, provider-error states, keyboard navigation, horizontal table scrolling, and disclosures. The `checks.json` artifact records each of the 39 outcomes.

The desktop screenshot was visually inspected. Its summary table displays the third buy-and-hold result and an `年化波動度` column for all three rows. The captured real-application API response contains numeric `annualizedRealizedVolatility` values for each result (approximately 25.20%, 39.23%, and 28.57% for this synthetic fixture), and 260 daily equity observations for the selected strategy. See [desktop API response](evidence/m1-browser/desktop-ma-crossover-api.json) and [desktop screenshot](evidence/m1-browser/desktop-result-details.png). These are generated-fixture outputs, not market-performance evidence.

The failure scenarios intentionally returned HTTP 502/422 and corresponding browser console resource errors. They were expected fixture cases; the UI displayed errors and suppressed partial metrics. The runtime check passed with no uncaught page errors, failed network requests, or unexpected HTTP errors.

## Limits

The 768 × 1024 and 390 × 844 contexts are browser web viewports. This run did not use Android Emulator, iOS Simulator, or a physical device. Synthetic weekdays are not an authoritative TWSE calendar; it does not verify live TWSE data, licensing, corporate actions, dividends, or real trading outcomes. No user research was performed.
