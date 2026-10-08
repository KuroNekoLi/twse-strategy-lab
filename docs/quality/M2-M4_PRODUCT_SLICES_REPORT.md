# M2–M4 產品切片交付紀錄

日期：2026-10-09。此紀錄補充 `M1-M4_PROGRESS_REPORT.md`，不更改使用者撰寫的 `產品規劃.md`。本次把可安全交付的本機／合成資料功能做成可操作頁面與 API；不代表 M2、M3 或 M4 的完整產品里程碑已完成。

## 已交付

- **M2 我的研究**：新增設定版本清單與參數差異比較；由清單載入特定設定版本到回測工作台。只讀既有本機白名單設定，不保存行情或績效結果，也不改寫無法辨識的儲存資料。
- **M3 模擬交易練習**：新增本機虛擬帳戶、事件重播、訂單狀態操作、使用者輸入參考價成交、持倉及帳務歷史。交易金額以整數分計算，帳戶由 append-only 事件重建；損壞資料進入唯讀，錯誤不覆寫原始資料。頁面明示沒有行情、券商、真實撮合、交割與官方費率。
- **M4 決策練習**：新增使用固定合成樣本的 session API 及逐步決策頁。回應僅含目前觀察和已記錄事件；session 有數量與閒置時間上限，只存在服務程序記憶體。頁面提供即時讀屏播報。
- **M4 穩健性分析**：新增有限參數／成本案例的 API 和結果頁，輸出分布範圍與中位數，不挑選最高報酬案例。每次分析共用一份當次行情資料，不持久化；頁面明列資料、成本、授權及再現性限制。
- **導覽與易用性**：新增模擬交易與進階研究分組，窄視窗使用可展開主導覽；紙上交易使用單一頁面 landmark、可聚焦標題和完整深色工作區。

## 驗證

- 後端 `./mvnw -q test`：74 tests，0 failures、0 errors、0 skipped。
- 前端 `npm run typecheck`：通過。
- 前端 `npm run build:pages`：通過。
- `git diff --check`：通過。
- 獨立 Code Reviewer 最終結果：`ACCEPT`。已修正其發現的訂單取消轉移、穩健性限制字串契約、API 錯誤欄位、現金歸零後賣單及 live-region 初始掛載問題。
- 瀏覽器 QA：細節與結果見下方追加紀錄；未執行原生 iOS/Android 模擬器或真機測試。

## 仍未完成／阻塞

- M2 的回測結果歷史、結果比較與分享會保存或散布行情衍生資料；目前授權範圍未確認，因此未做。
- M3 目前是本機教學帳本，參考價由使用者輸入；官方交易日曆、公司行動、T+2／券商規則、真實盤後價格及其保存權利仍未驗證。
- M4 決策練習使用合成資料，不能當作歷史績效或歷史行情 replay；真實歷史資料來源、授權、跨期間／樣本外驗證與真人學習驗證仍缺。
- 穩健性頁面若實際送出分析，會請後端透過既有行情來源載入資料。授權與資料完整性尚未確認前，本次未以真實行情執行該頁分析。
- M1 新手研究流程仍需目標使用者研究；本次 code review 與瀏覽器操作不能取代真人研究。
- 未保存瀏覽器截圖或 trace 檔；QA 工具只提供當次畫面，無可用的本機匯出介面。瀏覽器留下本次測試建立的本機研究設定及紙上交易帳本；未清除 append-only 帳本。

## 實際 runtime 委派

| Agent identity | 任務 | 結果 |
|---|---|---|
| `/root/roadmap_audit` | 只讀檢查 M0–M4 路線圖缺口 | 指出使用者研究、M2 結果留存、M3 UI/API、M4 UI/API 等差距與外部限制。 |
| `/root/architecture_gaps` | 只讀檢查可安全實作的 M2–M4 切片 | 建議本機研究庫、合成 replay 及不誇大證據的穩健性頁面。 |
| `/root/paper_trading_ui` | 建立獨立 M3 本機模擬交易元件 | 已交付本機事件帳本 UI；未自行接導覽。 |
| `/root/replay_api` | M4 合成 replay API | 新增 bounded in-memory session API；synthetic contract tests 通過。 |
| `/root/robustness_api` | M4 穩健性分析 API | 新增 bounded matrix API；synthetic tests 通過，未呼叫即時行情。 |
| `/root/independent_review` | 獨立審查與修正後複審 | 最終 `ACCEPT`；發現均已修正。 |
| `/root/ux_review` | 獨立 UX／a11y 來源審查 | 促成模擬交易頁視覺與語意修正、進階研究分組及 replay 播報。 |
| `/root/browser_qa` | Chrome 實際操作 M2–M4 頁面 | Chrome 初次 API 流程受阻；臨時使用正確 host 後，根 agent 以 Chrome CUA 完成合成 replay 流程。 |

這些是實際 runtime subagent identity；安裝 agent 檔案本身不等於啟動 agent。

## 瀏覽器操作紀錄

`/root/browser_qa` 使用 macOS 桌面 Chrome 154.0.8037.98，1275×751：M2 儲存同一設定兩個版本並比對 20→25 通過；M3 建立 100,000 元虛擬帳戶、送出／接受／以輸入參考價 50 元成交 0050 十股通過（餘額 99,500、持股 10）；M4 穩健性頁顯示與揭露通過，但按要求未送出會讀取市場資料的分析。瀏覽器中的測試研究版本及 append-only 模擬帳本保留，未清除。

首次 M4 replay 瀏覽器請求設定到 `localhost:8081` 時，測試頁曾用 `127.0.0.1`，不符合 CORS 允許來源，回報失敗；後來 `/root` 在 `http://localhost:4200`、暫時 API 設定 `http://localhost:8081` 下重測，合成 session 顯示 2031-01-06 / 100，提交 WAIT 後前進到 2031-01-07 / 96，決策數 1，歷程與 `aria-live` 狀態更新。此為桌面 Chrome 瀏覽器 runtime，非原生模擬器或真機。

進階導覽連結在頁面 DOM 有正確可見文字與 `aria-label`，但 CUA 輔助功能快照仍只列出 URL，沒有連結名稱；無法透過現有工具進一步確認 Chrome 原生 accessibility tree，列為 `SOURCE_REVIEW_ONLY`／工具證據限制，不宣稱螢幕閱讀器驗收通過。QA 工具無法將截圖或 trace 匯出到 repo，故未留下可提交的截圖檔。
