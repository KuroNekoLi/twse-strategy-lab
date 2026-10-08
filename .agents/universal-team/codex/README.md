# Codex adapter (verified 2026-10-08)

Codex project skills are discovered under `.agents/skills/`. Project-scoped custom agents can be defined as TOML under `.codex/agents/`; this package provides 33 specialist templates under `.codex/agents-templates/`. Copy only selected templates into `.codex/agents/` when adopting them. The orchestrator remains the parent session and is instructed by root `AGENTS.md`.

Current Codex documentation describes app, CLI, and IDE subagent workflows triggered by a direct request or applicable `AGENTS.md`/skill instructions, subject to runtime/client/model availability. Role skills teach what work to perform; TOML names specialists; actual subagent threads are runtime execution. Record their real identities and returned findings.

Keep research/reviewer agents read-only. Assign non-overlapping paths to implementers. Subagents inherit the parent permission mode; a TOML profile does not authorize publishing, live spend, production changes or external messages. If true delegation is unavailable, report `SEQUENTIAL_SINGLE_AGENT`.

使用套件根目錄的 `INSTALL_AND_START_CODEX.md`，可要求 Codex 先安裝相關檔案至目前 repo，再依 prompt 立即執行使用者指定的需求。新寫入的 TOML 是否即時載入取決於當前 Codex session；必須先查明可呼叫 agent 和 subagent 工具。
