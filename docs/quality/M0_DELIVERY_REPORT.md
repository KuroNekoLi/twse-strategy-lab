# M0 交付紀錄

## 狀態

本機工程範圍完成。公開發布關卡仍 `BLOCKED`：月行情端點使用與儲存／再散布授權未知，且交易日曆、公司行動資料與歷史行為缺少獨立證據。沒有對外聯絡、部署或發布。

## 驗證

- 後端：`cd backend && ./mvnw --batch-mode verify`，Spring Boot `BUILD SUCCESS`，32 tests、0 failures/errors/skips。涵蓋手算 Golden fixtures、資料解析、服務、例外契約與 Spring HTTP 整合；包含 reviewer 指出的零現金／sub-cent 免費增持修補。
- 前端：`cd frontend && npm run typecheck && npm run build`，兩項成功。實際長序列元件方法量測 6 條×4,000 點共 40ms；該數據是方法量測，不是瀏覽器量測。
- 實際 Chrome：見 `M0_BROWSER_REPORT.md` 與 `evidence/m0-browser/`，39 PASS／0 FAIL；以本機合成行情，不是 TWSE 活資料。
- PRD：未改動；原始 staged／unstaged 修改保留。

## 實際 agent 身分與交付

| 角色 | runtime identity | 結果 |
|---|---|---|
| Orchestrator | `/root` | 安裝、整合、驗收、帳務缺陷修補 |
| Software Architect | `/root/m0_architect` | 唯讀架構／契約建議 |
| Data Engineer | `/root/m0_data` | 授權矩陣與行情規格；端點授權及發布狀態仍未知／BLOCKED |
| Backend Developer | `/root/m0_backend` | 引擎、服務、API、fixtures、wrapper；最後摘要回傳遇使用額度限制，由交付程式與測試證據確認 |
| Frontend Developer | `/root/m0_frontend` | Angular 展示口徑、限制揭露與前端檢查 |
| Code Reviewer | `/root/m0_reviewer` | 首審發現 P2 零成本免費持股；修補後複核通過，另外確認圖表快取 6×4,000 點為 40ms |
| Device & Browser Tester | `/root/m0_browser` | 實際 Chrome viewport 操作和 39 項驗收；最終文字 handoff 遇使用額度限制，瀏覽器原始 checks、runtime 環境、截圖及 trace 保存在 evidence 目錄 |

TOML profile 僅已安裝，沒有證據表示目前 runtime 已熱載入具名設定；上述是本次真正建立的臨時 subagent session。角色技能與交付範圍見 `docs/product/M0_SCOPE.md`。

## 安裝結果

來源套件 `.agents/skills/` 與 `.agents/universal-team/` 同路徑安裝，共 52 個檔案；目標 repo 先前沒有同名檔案衝突。新增六份任務相關 `.codex/agents/*.toml`，合併通用協作規則到根 `AGENTS.md`。`docs/quality/team-installation.json` 記錄 52 個檔案、6 個 TOML 與 34 個 skill registry references 的檢查結果。

## 尚未驗證／阻塞

未執行原生 Android／iOS、實體裝置、跨瀏覽器、真人研究、live TWSE 資料或法律授權審核。合成資料不能清除授權及來源品質阻塞。不得將 `LIMITED_RESEARCH` 或目前策略結果描述為完整可發布投資回測。
