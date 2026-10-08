# M1–M4 進度與驗收紀錄

此紀錄接續 `docs/quality/M0_DELIVERY_REPORT.md`。原始 `產品規劃.md` 保留使用者既有 staged／unstaged 變更，未被本次修改。

> 2026-10-09 更新：M2–M4 新增可操作產品切片的最新範圍、瀏覽器 QA 與剩餘阻塞，見 [`M2-M4_PRODUCT_SLICES_REPORT.md`](M2-M4_PRODUCT_SLICES_REPORT.md)。以下舊表格保留當時進度背景；M2–M4 狀態以該報告為準。

## 安裝

- 來源套件 `.agents/skills/` 與 `.agents/universal-team/` 安裝到目標同路徑，共 52 個檔案；合併通用協作規則到根 `AGENTS.md`，保留既有專案規範。
- 安裝 6 份與本次工作相關的 `.codex/agents/*.toml`；模板沒有直接當作 agents 目錄使用。檔案結構、TOML 語法、skill references 已核對。Codex 目前 runtime 是否熱載入這些 TOML 未驗證；本紀錄的工作者是實際建立的臨時獨立 subagent session。

## 里程碑狀態

| 里程碑／切片 | 狀態與證據 | 未交付／限制 |
|---|---|---|
| M0 基礎 | 完成，細節見 `M0_DELIVERY_REPORT.md`。 | 發布仍受歷史行情授權、日曆與公司行動資料阻擋。 |
| M1 引擎／目錄 | 買進持有基準、年化波動度已完成。目錄搜尋、來源揭露、不相容基金限制已完成；code review `ACCEPT`，合成資料 Chrome 9/9，見 `M1_CATALOG_BROWSER_REPORT.md`。 | 結果匯出／分享會攜帶行情衍生資料，授權未確認前不提供；真人研究工作流尚未驗證。因此 M1 整體仍未完成。 |
| M2 個人研究室 | 具名設定、版本、載入、刪除、本機白名單與錯誤保護完成；review `ACCEPT`。Chrome 初次 12/12（含 quota），修正後獨立回歸 8/8（含重複 ID 注入），細節見 `M2_BROWSER_REPORT.md`。 | 只保存設定，不保存行情／結果；沒有研究歷史、結果比較、分享或雲端同步。 |
| M3 模擬交易 | 純記憶體帳本與純訂單狀態機已完成切片；各自 code review `ACCEPT`。 | 尚無 API/UI、持久化、保留資金／結算整合或行情 feed；交易日曆、公司行動與券商規則未驗證，不能稱為可用模擬交易產品。 |
| M4 學習／進階研究 | 純領域遮蔽未來 replay 與離線穩健性矩陣完成切片；兩者 review 均 `ACCEPT`。 | 尚無 API/UI、行情載入／持久化、學習任務或真人學習驗證；穩健性分析只對呼叫端提供資料運算。 |

## 實際委派與結果

下列 `/root/...` 是本次建立且有獨立 session/thread 的 runtime identity；不是從複製 agent 設定推定已啟動。

| runtime identity | 派發範圍 | 回傳結果 |
|---|---|---|
| `/root/m0_architect`、`/root/m0_data`、`/root/m0_backend`、`/root/m0_frontend`、`/root/m0_reviewer`、`/root/m0_browser` | M0 架構、來源、引擎、UI、獨立審查與 Chrome | 結果及證據記於 `M0_DELIVERY_REPORT.md`；review 找到並修正免費持股邊界，Chrome 39 項通過。 |
| `/root/m1_frontend_catalog` | M1 目錄搜尋 UI | 加入代碼／名稱搜尋、來源揭露、加入相容代碼與基金格式限制；typecheck/build 通過。 |
| `/root/m0_backend` | M1 目錄 API、parser、短期記憶體快取與合成契約測試 | 10 個新增目錄測試；當時完整 verify 45 tests 通過。只測 synthetic transport，未讀即時來源。 |
| `/root/m1_catalog_review` | M1 前後端整合獨立 review | 找到 STOCK/COMPANY 標籤錯配；修正並複審 `ACCEPT`。也確認 zoneless 更新修正與 stale request guard。 |
| `/root/m1_catalog_browser` | M1 目錄合成資料 Chrome 操作 | 首次發現 zoneless 畫面卡載入；修復後 9/9 通過。Chrome 154、macOS arm64、1440×1100，無即時行情呼叫。 |
| `/root/m2_local_config` | M2 本機設定實作與 Chrome 驗收 | 實作 schema v1 設定白名單；初測發現數字欄位型別不一致並修正，Chrome 12/12 通過，含模擬 quota。 |
| `/root/m2_review` | M2 獨立 code review | 找到重複版本 ID 可讓一次刪除移除多筆；加入唯一性驗證後複審 `ACCEPT`。 |
| `/root/m2_browser_regression` | M2 獨立 Chrome 回歸 | Chrome 154、390×844；8/8 通過，包括 malformed／unknown schema、版本刪除與重複 ID 唯讀保護。此輪 quota 注入未完成；quota PASS 保留於前次 12/12 證據，未合併成同一輪宣稱。 |
| `/root/m3_ledger_engine` | M3 純帳本 Golden slice | 新增帳本／投影；review 找到低於一分免費持股邊界，修正後 6 tests 通過。 |
| `/root/m3_ledger_review` | M3 帳本獨立 review | 修正後 `ACCEPT`；獨立重跑 6 tests 通過。 |
| `/root/m3_order_lifecycle` | M3 純訂單狀態機 Golden slice | review 找到事件可缺少時間／生效日期；改為必填並新增測試，修正後 8 tests 通過。 |
| `/root/m3_order_review` | M3 訂單狀態機獨立 review | 修正後 `ACCEPT`；獨立重跑 8 tests 通過。 |
| `/root/m4_replay_engine` | M4 遮蔽未來 replay Golden slice | 四項合成 Golden tests 通過；未接行情/API/UI。 |
| `/root/m4_replay_review` | M4 replay 獨立 review | `ACCEPT`；限定純領域範圍，未評 API/UI。 |
| `/root/m4_robustness_engine` | M4 離線穩健性矩陣 | 最多 25 個案例，輸出績效 min／median／max、不做最佳案例排序；3 項 Golden tests 通過。 |
| `/root/m4_robustness_review` | M4 穩健性獨立 review | `ACCEPT`；指出偶數樣本 median 測試未斷言精確中點，無確認缺陷。 |

## 本次整合檢查

- `cd backend && env JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home ./mvnw --batch-mode verify`：BUILD SUCCESS，66 tests、0 failures/errors/skips，Java 17。
- `cd frontend && npm run typecheck && npm run build`：兩項成功。
- `git diff --check`：成功。
- 實際瀏覽器證據只涵蓋 M0、M1 目錄搜尋及 M2 設定流程。M3/M4 目前為純領域合成測試，尚無可操作 UI；不能列為 `BROWSER_RUNTIME` 通過。

## BLOCKED 與未驗證

- 歷史行情的使用、保存及再散布權利未確認；目錄 OGDL 授權不延伸至月行情或衍生回測結果。故 M1 匯出分享、M2 歷史結果留存，以及 M3/M4 的行情載入、結果長期保存／分享仍未開放。
- 未驗證官方交易日曆、公司行動、真實結算規則、即時來源 SLA、Android/iOS 原生模擬器、實體裝置或真人研究／學習成效。
- 本次沒有改動 PRD、發布、推送、部署、外部聯絡或生產系統。
