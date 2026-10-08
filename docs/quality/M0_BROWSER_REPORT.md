# M0 瀏覽器與 API 獨立驗證

- 派發任務：`M0-BROWSER`；runtime identity：`/root/m0_browser`。
- 技能：Device & Browser Tester、QA Engineer；唯讀產品程式，寫入範圍為本報告、`docs/quality/scripts/`、`docs/quality/evidence/`。
- 結果（M0 執行時）：`BROWSER_RUNTIME`，39 PASS／0 FAIL。實際操作本機 Angular 頁面與 Spring Boot API；上游是本機合成資料服務。
- 瀏覽器：macOS Darwin 25.6 arm64、Google Chrome 154.0.8037.98 headless。桌面 1440×1000、平板 768×1024、手機網頁視窗 390×844；沒有原生模擬器、實體裝置、真人研究或跨瀏覽器證據。
- M0 最終 jar SHA256 為 `97df1d59d57af494092856faac4e993fbaece6f6306915c694c8848def6214cd`，前端 `main-GIGPD2CK.js` SHA256 為 `f25e5d15dd95777ffd8ab9ffa185b4f52045a931a8b13a5567be820f7515b166`。M0 執行當時環境紀錄顯示 39/0；其後 M1 瀏覽器執行覆寫了 `evidence/m0-browser/`，目前 M1 的完整且隔離複本位於 `evidence/m1-browser/`。因此現有 m0-browser 目錄不是原始 M0 run 的獨立歸檔。

## 驗證範圍與證據

實際通過項目包括：五種策略、成功與 loading、輸入拒絕、明確零稅率、標的期間與結果呈現、資產／TWR／來源與限制、鍵盤操作與小螢幕溢位、上游／空資料／壞列錯誤、決定性、每日帳務對帳、下一筆觀察收盤成交日期及錯誤請求。M0 執行當時檢查 1,038 個每日估值、48 筆成交以及 24 筆下一觀察日成交。M0 原始 checks／API／截圖／trace 未另行保存；其後執行且隔離保存的 M1 瀏覽器證據 `evidence/m1-browser/` 含相同 M0 情境並額外驗證 M1 買進持有與波動度 UI，作為目前可用的回歸證據。M0 當時有 3 筆預期 HTTP 錯誤及其 Chrome console 對應訊息，無未處理頁面錯誤或非預期網路失敗。

重跑方式：

```sh
node docs/quality/scripts/m0-browser.cjs
```

腳本只啟動其自有 localhost 測試服務並關閉自有程序，不安裝相依套件、不修改產品執行設定。合成平日行情不是真實 TWSE 行情或權威交易日曆；這些結果不證明資料授權、歷史公司行動、實際市場品質、真人可用性或公開發布資格。
