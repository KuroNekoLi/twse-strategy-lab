# Fugle 歷史日 K 回補操作與正式環境閘門

日期：2026-10-10

## 現況

後端新增 Fugle 歷史日 K adapter，可依 API 限制把超過一年的期間拆成多次請求，驗證標的、日期、OHLCV 後寫入既有市場資料庫 SSOT。0050 自 2010 年回補預設至少要求 3,500 筆，低於門檻會拒絕保存；若調整標的或期間，必須同步設定合理的 `APP_HISTORICAL_BACKFILL_MINIMUM_ROWS`。筆數門檻只能攔截明顯不完整的回應，不能證明每個交易日都已覆蓋；資料品質仍標示交易日覆蓋未知。資料仍標記 `UNVERIFIED`；目前資料庫歷史 API 不會公開回傳未核准來源的資料。

使用者已確認產品允許本站回補、保存與公開展示歷史行情。這是產品需求確認，不是 Fugle、時報資訊、TWSE 或 TPEx 對本站授予資料儲存及公開展示權的證明。Fugle 公開規範要求使用者遵守來源交易所規範，並限制把行情傳送予第三人；部署前必須取得涵蓋本網站使用方式的書面授權／方案確認。不要只把 `APP_MARKET_DATA_ALLOW_UNVERIFIED_DISPLAY` 設成 true 來繞過授權閘門。

## 正式回補設定

確認供應商授權及 API 帳號後，在 Zeabur 後端服務設定：

```text
APP_MARKET_DATA_HISTORY_SOURCE=fugle
FUGLE_API_KEY=<在 Zeabur Secret 設定，不要放進 repo>
APP_HISTORICAL_BACKFILL_ENABLED=true
APP_HISTORICAL_BACKFILL_SYMBOL=0050
APP_HISTORICAL_BACKFILL_FROM=2010-01-01
APP_HISTORICAL_BACKFILL_TO=today
APP_HISTORICAL_BACKFILL_MINIMUM_ROWS=3500
```

回補 runner 在服務啟動時執行一次；完成後應關閉 `APP_HISTORICAL_BACKFILL_ENABLED`，避免每次重啟都重抓整段歷史。資料庫 upsert 具冪等性，但部署多個副本時仍會重複呼叫上游，因此應以單副本執行初次回補。官方 API 單次日期範圍須短於一年，adapter 會自動分段；超過 API 權限或速率限制仍可能失敗。回補錯誤會安全記錄並讓應用程式啟動，不會把不完整批次當成成功資料。

目前 adapter 的來源權利狀態仍為 `UNVERIFIED`。取得書面許可後，才更新 Fugle source metadata 的授權狀態與公開歸屬文字，重新部署後驗證 `/api/v1/stocks/0050/history`。驗收至少確認 2010 年起的可用資料期間、五年區間約千筆日 K、OHLCV 完整性，以及週／月 K 聚合；核對資料庫最早/最新日期與畫面顯示一致。公司行動調整政策仍需明確標示。

## 已知能力與限制

- Fugle 歷史 K 線文件列出上市櫃個股日資料可回溯至 2010 年，並支援日／週／月 K；單次區間必須短於一年。
- Fugle API key 只供後端讀取；禁止放進 Angular 設定、前端 bundle 或日誌。
- 本次只新增歷史日線回補，不代表即時行情、分鐘 K 或即時行情公開展示權已確認。
- 只完成 mock HTTP contract tests；尚未用正式 Fugle API key 呼叫實際上游，也尚未在 Zeabur 回補或驗證完整圖表。

## 來源

- Fugle Historical Candles API：https://developer.fugle.tw/docs/data/http-api/historical/candles/
- Fugle API 使用規範：https://developer.fugle.tw/docs/data/intro/
- Fugle 台股行情方案及價格：https://developer.fugle.tw/docs/pricing/
