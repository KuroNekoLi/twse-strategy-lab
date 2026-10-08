# M2 Local Configuration Browser Report

## Independent duplicate-ID regression run

**Status: PASS (8/8 checks in this run).** Labels: `BROWSER_RUNTIME`, `AUTOMATED_TEST`.

- Runtime identity: `/root/m2_browser_regression`.
- Browser: Google Chrome 154.0.8037.98, headless Playwright; macOS Darwin 25.6.0 arm64; Node v22.22.3.
- Target: production Angular build served at `http://127.0.0.1:14212`, viewport 390 × 844 CSS px, device scale factor 1. This is a web viewport, not a native mobile device or simulator.
- Build command: `npm run typecheck && npm run build` (both passed).
- Browser command: `node /tmp/m2-browser-regression.cjs`.
- No live market/catalog requests were made. Storage cases used synthetic browser-local values.
- Evidence: [mobile web screenshot](evidence/m2-browser/m2-regression-mobile.png), [malformed-storage screenshot](evidence/m2-browser/m2-malformed-readonly.png), [duplicate-ID screenshot](evidence/m2-browser/m2-duplicate-id-readonly.png), [check results](evidence/m2-browser/m2-regression-checks.json), [runtime/build metadata](evidence/m2-browser/m2-regression-environment.json).

The eight passing checks covered the device-only/no-cloud disclosure; saving all 19 whitelisted configuration fields without market/result payload; reload and load of v1; adding v2 while preserving and loading both versions; deleting only v2; preserving malformed JSON and disabling writes; preserving an unknown schema and disabling writes; and rejecting duplicate version IDs while preserving the exact raw value, disabling saves, and rendering no ambiguous delete controls. The 390 px viewport had no horizontal document overflow and no uncaught page errors.

The independent code reviewer `/root/m2_review` rechecked the duplicate-ID fix and returned `ACCEPT`; the earlier “review pending” note is superseded.

## Earlier M2 runtime run

The prior run by `/root/m2_local_config` recorded 12/12 checks passing in Google Chrome 154.0.8037.98 on macOS Darwin arm64 at 390 × 844 CSS px. Its evidence remains in [earlier screenshot](evidence/m2-browser/m2-local-config-mobile.png) and [earlier Playwright trace](evidence/m2-browser/m2-local-config-trace.zip). That run included the simulated `QuotaExceededError` case and reported storage remained unchanged. Its exact runtime identity is distinct from the independent regression run above.

## Limits

The independent regression rerun did not complete a second quota-injection check: the browser harness stalled while trying to interact with the form after setting the storage override, so that case is not counted among the eight checks. The earlier runtime report contains the successful quota result. No Android/iOS simulator or physical device, cloud sync, research-history, sharing, live market data, or native-device behavior was tested. The project has no established frontend unit-test framework.
