# M1 標的目錄搜尋 — 瀏覽器驗收

**結果：15/15 PASS**（`BROWSER_RUNTIME`, `AUTOMATED_TEST`）。這是標的目錄功能驗收，不代表 M1 整體完成。

## 執行環境

- 實際瀏覽器：Google Chrome 154.0.8037.98，headless，macOS 26.6、Darwin 25.6.0、arm64。
- 瀏覽器視窗：1440 × 1100；本次只測桌面瀏覽器，未測原生 Android/iOS 模擬器或實體裝置。
- 本機 Angular 頁面、Spring Boot API 與合成 upstream fixtures；沒有讓瀏覽器測試依賴外部交易所當下可用性。
- 執行命令：`node docs/quality/evidence/m1-browser-catalog/run.cjs`。
- 合成目錄包含上市公司、上市基金與上櫃公司；含 0050、0051、2330、2603、2882、00400A 及上櫃 1240。

## 驗收結果

1. 探索頁代碼搜尋 `2330`：顯示台積電、上市市場與可帶入回測狀態。
2. 探索頁單字搜尋 `台`：顯示台積電、台達電、台塑等簡稱。這個場景修正了原本需輸入兩字才會送出查詢的 UI 限制。
3. 探索頁 `1240`：顯示上櫃及行情尚未支援，不提供誤導性的帶入操作。
4. 回測工作台代碼搜尋 `2330`：載入狀態出現後清除，顯示上市公司名稱。
5. 代碼前綴 `005`：回傳 0050、0051 等符合項目。
6. 中文名稱搜尋 `台`：優先顯示台積電、台達電、台塑等公司簡稱。
7. 來源與限制：畫面顯示三個資料集、OGDL v1.0、月／日更新頻率、出表日期、取得時間與限制。
8. 重複代碼：既有 `0050` 顯示「已加入」且按鈕停用。
9. 名稱搜尋 `長榮`：`2603` 可選並加入清單。
10. 上櫃代碼 `1240`：可搜尋但不能加入 TWSE 回測清單，並顯示行情限制。
11. 不相容基金代碼 `00400A`：顯示基金目錄與格式原因，不能加入回測清單。
12. 清單上限：加入三檔後，第四檔顯示「已達上限」且不能加入。
13. 無結果：`NO-MATCH` 顯示「沒有符合的目錄項目」。
14. 錯誤狀態：合成 503 API 回應顯示查詢錯誤，清除先前結果；console 唯一錯誤是預期中的合成 503。
15. 競速狀態：延遲舊查詢並先回傳新查詢；舊結果沒有覆蓋目前結果。

## 另行進行的官方來源驗證

以 Java 17、`local` profile 連線官方 TWSE／TPEx 目錄來源，首次查詢同步至 H2 約 2,259 筆；API 查詢 `005` 回傳 12 筆（包括 0050、0051），查詢 `台` 回傳 114 筆（前 20 筆含台塑、台達電、台積電）。這是當次唯讀實際回應，不是資料完整度或來源 SLA 保證。瀏覽器報告中的互動及錯誤路徑仍使用固定合成 fixtures，兩類證據不能混為一談。

## 證據

- `evidence/m1-browser-catalog/checks.json`：各驗收項目與實際結果。
- `evidence/m1-browser-catalog/environment.json`：瀏覽器、作業系統、視窗大小、fixtures、來源請求與產物 SHA-256。
- `evidence/m1-browser-catalog/explore-company-search.png`、`company-search-attribution.png`：探索與回測搜尋畫面。
- `evidence/m1-browser-catalog/selected-company.png`：加入代碼後的清單。
- `evidence/m1-browser-catalog/latest-query-result.png`：競速測試後保留的新查詢結果。
- `evidence/m1-browser-catalog/catalog-trace.zip`：Playwright trace，含截圖與 DOM/network 操作追蹤。
- `evidence/m1-browser-catalog/run.cjs`：可重跑的本機合成資料驗收腳本。

合成測試只驗證前端互動及本機 API 契約；未驗證上游 SLA、整體目錄完整性、基金行情相容性，也沒有解除歷史行情及回測發布的授權阻擋。
