# FinMind 歷史行情回補與展示狀態

## 已驗證的資料來源

FinMind `TaiwanStockPrice` 公開 API 可回傳個股日 OHLCV。2026-10-10 對 0050 查詢 2021-10-10 至 2026-10-10，實測 HTTP 200、1,208 筆，觀測日期為 2021-10-12 至 2026-10-08。這確認資料可取得，不能據此推論原始資料可在公開網站重製或展示。

## 實作與 SSOT

- FinMind 是獨立 `HistoricalMarketDataSource`，不取代每日全市場政府資料快照來源。
- 一次性歷史回補先在來源端完成抓取與驗證，再以單一資料庫交易寫入 `daily_market_bar`；圖表仍由資料庫 provider 讀取，不在每次頁面請求時呼叫外部 API。
- `(symbol, trading_date, adjustment_policy)` 維持唯一；明確回補可更新重疊日期的 source lineage。之後每日快照遇到不同 canonical source 時會略過該列並記錄衝突數，不覆蓋歷史來源。
- 每個來源保存獨立 `licensing_status`。歷史查詢跨越多個來源時，只要有任一來源不是 `CONFIRMED`，整個查詢期間都標示 `UNVERIFIED`；一般環境預設不回傳這些 K 棒。
- 日 K 的週 K／月 K 仍由既有 chart service 聚合，不另外存重複週/月資料。
- OHLC 以 `RAW` 原始價格儲存，尚未處理股利、分割等公司行動；缺漏日期完整性仍未知。

## 本機 H2 實際回補

回補 runner 預設關閉。可在 backend 目錄以 local profile 明確啟用；此命令只使用 local profile 的記憶體 H2，並於應用程式結束後清除資料：

```sh
APP_MARKET_DATA_INGESTION_ENABLED=false \
APP_FINMIND_BACKFILL_ENABLED=true \
APP_FINMIND_BACKFILL_SYMBOL=0050 \
APP_FINMIND_BACKFILL_FROM=2010-01-01 \
APP_FINMIND_BACKFILL_TO=today \
APP_MARKET_DATA_ALLOW_UNVERIFIED_DISPLAY=true \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

runner 完成後，透過 `GET /api/v1/stocks/0050/history?from=2021-10-10&to=2026-10-10&interval=1d` 檢查回應的 `bars.length > 1`，並確認 `licensingStatus=UNVERIFIED`。目前 API 每次查詢最多五年，因此 2010-2026 回補需分段讀取驗收。

## 正式環境啟用條件

FinMind 的 API 存取可用性和原始資料公開展示授權是兩件事。部署環境的 backfill 與未確認資料展示開關維持關閉；在取得並核實原始資料權利人允許快取及公開展示前，不要在 Zeabur 開啟匯入或展示設定，也不要把本機 H2 的成功視為正式環境已具備歷史資料。

資料來源文件：[FinMind TaiwanMarket API](https://finmind.github.io/tutor/TaiwanMarket/Technical/)、[FinMind disclaimer](https://finmind.github.io/Disclaimer/)、[FinMind pricing](https://finmind.github.io/Pricing/)。
