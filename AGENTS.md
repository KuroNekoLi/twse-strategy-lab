# TWSE Strategy Lab — Project Guidance

- Preserve Angular 22 in `frontend/` and Kotlin / Java 25 / Spring Boot 4.1 in `backend/`.
- Read `產品規劃.md` as the product roadmap; execute a bounded milestone with explicit acceptance. M0 foundation is the currently authorized scope.
- Preserve user-authored and staged changes in `產品規劃.md`; add execution notes under `docs/` instead.
- Use synthetic fixtures for deterministic accounting tests. Keep live market-data checks separate and label licensing, dividends, corporate actions and missing-calendar limitations accurately.
- Frontend checks: `npm run typecheck`, `npm run build`; backend checks: Maven tests / package when tooling is available. Do not claim a build as browser or market-data verification.
- No publish, push, deployment, broker orders, production writes, external messages or system dependency installation without explicit authorization.

# Universal Multi-Agent Development Team

Use the role skills in `.agents/skills/` and team workflows in `.agents/universal-team/`. This file is project-level guidance; a role skill alone does not spawn an agent.

- First inspect project conventions, the request, and whether the platform exposes actual subagent tools. Delegate bounded independent tasks when available; record runtime identities and return artifacts. Otherwise mark `SEQUENTIAL_SINGLE_AGENT`.
- For feature proposals or vague new requests, route through `.agents/universal-team/workflows/idea-assessment.md`. If implementation is explicitly requested, preserve that scope and use its approved acceptance criteria.
- Invoke only relevant specialists. Keep reviewers independent of implementer context; avoid simultaneous edits to shared files.
- Apply `.agents/universal-team/quality-gates.md`; report evidence and unverified behavior accurately.
- Preserve this project’s framework and data policies. A workspace instruction never authorizes release, live spend, external messages, or production changes.

## Framework Engineering Standards

- Before frontend architecture or implementation work, read `.agents/skills/angular-development/SKILL.md` and its `references/SOURCES.md`; frontend developers must follow it.
- Before backend architecture or implementation work, read `.agents/skills/spring-boot-kotlin-development/SKILL.md` and its `references/SOURCES.md`; backend developers must follow it.
- Architecture decisions spanning either stack must use both framework standards and `.agents/skills/software-architect/SKILL.md`. Code review must use `.agents/skills/code-reviewer/SKILL.md` plus the applicable framework standard, and review actual changed code.
- Existing project conventions and explicit user requirements take precedence over generic external recommendations. Do not introduce framework migrations, new libraries, or repo-wide cleanup merely to match a guideline; propose separately when justified.
- Treat `.agents/skills/architecture/ENGINEERING_STANDARDS_AND_ARCHITECTURE_AUDIT.md` as the evidence-backed baseline. Its findings describe current debt, not automatic authorization for broad refactors; update the audit when material architecture changes land.
