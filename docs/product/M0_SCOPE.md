# M0 執行範圍與驗收

決策日期：2026-10-09（Asia/Taipei）。來源：根目錄《產品規劃.md》4.1、6、7、8、11.4、13.1、16.1。使用者已選擇「先完成 M0 基礎」。原 PRD 含 staged／未 staged 使用者修改，保留原文與 Git index。

## 交付邊界

本次交付 EPIC-01～03 的工程基礎：來源／授權盤點、明確的收盤代理模型、版本化輸入輸出、十進位帳務、整數股數、可追溯成交／每日資產與固定合成行情驗證。沿用 Angular 22、Java 17 原始碼相容性與 Spring Boot 4.1。前端只做計算口徑、資料限制與契約相容性所需的調整。

不在本次範圍：M1 標的目錄／多頁工作台、M2 研究保存、M3 虛擬交易帳戶、M4 回放、NEXT_OPEN、即時行情、券商下單、資料庫重構、公開發布。市場資料授權、可靠交易日曆與完整公司行動來源不能用合成測試代替。

## 驗收條件

| ID | 條件 | 實際驗證層 |
|---|---|---|
| AC-01 | 消費欄位有來源、單位、正規化方式與授權狀態；公開可讀不推定可儲存／再散布 | 原始碼追蹤＋官方來源盤點 |
| AC-02 | 報告保留請求與實際涵蓋期間、引擎／策略／成本／規則／調整版本及行情 SHA256；同一份固定資料與輸入可重現 | 契約／固定資料測試 |
| AC-03 | 策略訊號只讀已完成歷史，T 日訊號於下一筆觀察資料的收盤代理計價；無日曆不得稱「已證明無缺漏的下一交易日」 | 手算訊號／成交日期＋未來資料擾動測試 |
| AC-04 | 整數股數、殘餘現金、十進位金額及明確費用捨入；買賣後現金與部位可由明細核對 | Golden Fixtures 手算預期 |
| AC-05 | 淨投入比率與 TWR 分開，年化基於已定義 TWR；每日回撤排除外部投入的直接影響 | 固定價格／月初投入／回撤測試 |
| AC-06 | 無效／重複／順序錯誤／非正價格／不完整上游資料不靜默成功，錯誤保留既有 error 並可辨識 code | 引擎／來源／服務測試 |
| AC-07 | 明確揭露 RAW_CLOSE、股利／公司行動／日曆／最低手續費／滑價等未支援能力，授權與資料品質未知時發布狀態為 BLOCKED | 契約、文件與瀏覽器 |
| AC-08 | 獨立 reviewer 審查實際 diff；瀏覽器操作成功、錯誤、載入與小螢幕相容性並留下截圖／trace；不冒充真人用戶研究／原生裝置驗證 | Code Review＋BROWSER_RUNTIME |

## 依賴與完成定義

工程驗收通過，代表上述已實作的範圍通過所列證據；不等於整份 M0 產品發布關卡已清除。活資料只有 hash 而未封存時，只可追蹤本次輸入，不能保證多年後重取相同資料。合成行情可重播的證據須與活行情封存／授權證據分開。

股利入帳、分割帳務、停牌及交易日缺漏須列為未支援／BLOCKED，直到有合法且可追溯的事件及日曆資料並增加獨立手算 fixtures。現行代碼猜測 ETF 稅率只能是版本化研究假設，不代表歷史法規或全商品分類引擎。

## 分工與寫入範圍

- `/root`：orchestrator／PM，安裝、範圍、工具、CI、整合及最終證據。使用套件 orchestrator、product-manager、QA 技能。
- `M0-ARCH`：Software Architect，唯讀原始碼與 PRD，交付設計與契約建議。
- `M0-DATA`：Data Engineer，僅 `docs/specifications/MARKET_DATA_SPEC.md`、`DATA_LICENSING_MATRIX.md`。
- `M0-BE`：Backend Developer／Test Automation Engineer，僅 `backend/`、引擎／API 規格與 Golden Fixtures 清單。
- `M0-FE`：Frontend Developer，僅已分派的 `frontend/`，等待確定的回應契約。
- `M0-REVIEW`：Code Reviewer，於 diff 可審後開始，唯讀且不接收實作者對話。
- `M0-BROWSER`：Device & Browser Tester／QA，操作本機測試服務，僅 QA 證據與測試腳本；產品程式唯讀。

實際建立的 runtime identity 與各次回傳會記入 `docs/quality/M0_DELIVERY_REPORT.md`。安裝 TOML 與啟動 worker 分開記錄；本次使用 runtime 可建立的臨時獨立 subagents，未確認熱載入具名設定。
