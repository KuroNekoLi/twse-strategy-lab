# M1 標的目錄搜尋 — 瀏覽器驗收

**結果：9/9 PASS**（`BROWSER_RUNTIME`, `AUTOMATED_TEST`）。這是 M1 目錄搜尋的範圍驗收，不代表 M1 整體完成。

## 執行環境

- 實際瀏覽器：Google Chrome 154.0.8037.98，headless，macOS 26.6、Darwin 25.6.0、arm64。
- 瀏覽器視窗：1440 × 1100；本次只測桌面瀏覽器，未測原生 Android/iOS 模擬器或實體裝置。
- 本機 Angular 頁面、Spring Boot API 與合成 upstream fixtures；沒有呼叫即時 TWSE 行情或目錄服務。
- 執行命令：`node docs/quality/evidence/m1-browser-catalog/run.cjs`。
- 合成目錄包含上市公司 0050、2330、2603、2882，以及可搜尋但與現有行情格式不相容的基金代碼 00400A。

## 驗收結果

1. 搜尋代碼 `2330`：載入中狀態可見後消失，顯示 `台積電` 和「上市公司」標籤。
2. 來源與限制：畫面顯示兩個資料集、OGDL v1.0、月更新頻率、來源出表日期、取得時間、快取時間及限制；連結分別指向兩個官方資料集與授權說明。
3. 重複代碼：既有 `0050` 顯示「已加入」且按鈕停用。
4. 名稱搜尋 `長榮`：`2603` 可選並加入清單。
5. 不相容基金代碼：`00400A` 顯示基金目錄與格式原因，不能加入回測清單。
6. 清單上限：再加入到三檔後，第四檔顯示「已達上限」且不能加入。
7. 無結果：`NO-MATCH` 顯示「沒有符合的目錄項目」。
8. 錯誤狀態：合成 503 API 回應顯示查詢錯誤，並清除先前結果；唯一 console 訊息是此預期的合成 503 資源錯誤。
9. 競速狀態：故意延遲舊查詢、先回傳新查詢；舊結果沒有覆蓋目前結果。

## 缺陷與修復回歸

首次實測發現 API 已回傳 200，但 Angular 22 zoneless 畫面停留在「搜尋中…」。實際回應內容與卡住畫面的截圖保存在 `evidence/m1-browser-catalog/debug-company.png`。加入異步狀態更新通知後，重新建置並重跑；上述 9 項全數通過，畫面已呈現搜尋結果且載入狀態清除。

## 證據

- `evidence/m1-browser-catalog/checks.json`：各驗收項目與實際結果。
- `evidence/m1-browser-catalog/environment.json`：瀏覽器、作業系統、視窗大小、fixtures 與產物 SHA-256。
- `evidence/m1-browser-catalog/company-search-attribution.png`：代碼搜尋結果與來源揭露。
- `evidence/m1-browser-catalog/selected-company.png`：加入代碼後的清單。
- `evidence/m1-browser-catalog/latest-query-result.png`：競速測試後保留的新查詢結果。
- `evidence/m1-browser-catalog/catalog-trace.zip`：Playwright trace，含截圖與 DOM/network 操作追蹤。
- `evidence/m1-browser-catalog/run.cjs`：可重跑的本機合成資料驗收腳本。

合成測試只能驗證前端互動和本機 API 契約；未驗證即時來源可用性、資料完整性、上游 SLA、基金行情相容性，也沒有解除歷史行情及回測發布的授權阻擋。
