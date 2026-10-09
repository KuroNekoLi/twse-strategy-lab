---
name: angular-development
description: Apply repository-compatible Angular architecture, implementation, and verification standards.
---

# Angular Development Standard — TWSE Strategy Lab

Read `references/SOURCES.md` before applying these practices. The current frontend is Angular 22, standalone components, TypeScript 6, RxJS 7, and Angular Router. Verify versions from `frontend/package.json` and the lockfile before framework-sensitive changes.

## Architecture and implementation

- Trace the route, owning page/component, shared state, API contract, and current interaction before editing. Keep feature behavior in its owning feature; move shared HTTP/domain transformations into focused injectable services when reuse or testability warrants it.
- Keep templates declarative and components focused on view state and interaction. Avoid growing an already large page component with unrelated features; split by cohesive feature boundary when changing it, without broad unrelated rewrites.
- Preserve standalone components and current Router conventions. Use typed route data and Router APIs. Consider lazy route loading for substantial pages/chunks when it has measurable loading or ownership benefit; do not change routing mechanics solely for style.
- Use explicit API/domain types and `unknown` with narrowing at untrusted boundaries. Do not add `any`, unchecked casts, or template `$any` as a convenience. Keep server DTOs separate from view-specific derived values where that prevents contract leakage.
- Prefer the established Angular forms and RxJS patterns in the touched feature. Signals or Signal Forms are options when they simplify that feature and fit the installed version; never migrate unrelated state/forms just because a newer API exists.
- Cancel or ignore stale requests, expose loading/empty/error states, and avoid duplicate requests caused by component lifecycle. Keep market values, timestamps, and data freshness source-labeled.
- Use Angular bindings and lifecycle APIs rather than manual DOM mutation. Preserve keyboard operation, visible focus, accessible names/status, responsive behavior, and the existing Traditional Chinese product language.
- Keep API base URL/environment configuration free of secrets and deployment-specific hardcoding. Do not weaken CORS or browser security to make a local feature work.

## Verification and review

- Run `npm run typecheck` and `npm run build` from `frontend/` for Angular source changes. Run relevant tests when present; this repository currently has no frontend test or lint script, so do not report those as run.
- Build/typecheck evidence is not browser interaction, accessibility, or device evidence. For interaction changes, use the project browser workflow when available and report browser/device identity and actual coverage.
- Review route reachability, loading/error/empty states, contract shape, stale-response behavior, keyboard/focus behavior, and mobile layout for changed flows. Record any relevant existing audit gap separately from a defect introduced by the patch.

