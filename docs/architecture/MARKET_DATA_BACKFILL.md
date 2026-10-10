# 台股行情回補、儲存與資料來源

本文說明目前專案如何取得、回補、保存和提供日線行情，以及部署時可調整的參數。設定名稱以 `backend/src/main/resources/application.yml`、`application-local.yml` 和程式實際行為為準；正式環境可能用 Zeabur 環境變數覆寫預設值。

## 先看結論

- 個股圖表讀取資料庫。查詢的標的／日期區間若有未驗證的缺口，啟用按需回補後才向所選歷史來源取得缺口並存入資料庫；相同範圍後續直接讀資料庫。
- 回補範圍是使用者這次查詢的範圍，不會因為查一檔股票就預先下載所有標的的全部歷史資料。
- 資料庫保存日 OHLCV。週 K、月 K 由日 K 在回應時彙整，不是另外請求供應商或另外存一份週／月資料。
- 正式環境曾以 0051 驗證：五年查詢回補並顯示 1,213 根日 K；後續相同區間讀取時 `fetchedAt` 不變。這證明了該標的與該期間的執行路徑，不代表上游支援的所有證券、期間都已逐一測過。
- 「任何標的」限於 API 接受的台股代碼，以及所選上游實際提供資料的標的／期間。無資料、上游拒絕、憑證失效或授權未確認時，系統不能保證取得資料，也不會補造行情。

## 一次個股歷史查詢的流程

個股 API：

```text
GET /api/v1/stocks/{symbol}/history?from=YYYY-MM-DD&to=YYYY-MM-DD&interval=1d|1w|1mo
```

流程如下：

1. `StockHistoryService` 檢查圖表開關、代碼、日期和週期。日期需自 2010-01-01 起、不可晚於台北今日，單次區間最多五年；API 代碼目前接受 4 至 6 位數字。
2. 預設的 `DatabaseHistoricalMarketDataProvider` 查詢 `market_history_coverage`，找出這個代碼在請求期間尚未成功檢查的日期缺口。日期計算以請求月份為單位，再限制回補結束日不晚於台北今日。
3. 若按需回補已開啟，而且所選來源的公開展示授權狀態為 `CONFIRMED`，系統逐個缺口呼叫 `MarketDataHistoryBackfillService`。在資料寫入前會檢查來源／標的／日期是否相符、OHLCV 是否合理；空結果回 `NO_MARKET_DATA`，不寫入假 K 棒。
4. 每個成功的回補交易會將行情列、來源資訊、匯入紀錄和覆蓋區間一併寫入資料庫。寫入失敗時不留下半套覆蓋紀錄；同一 JVM 的相同標的查詢會共用鎖，多個服務實例碰到唯一鍵競爭時會以新交易重試一次。
5. 系統重新從資料庫讀取日 K，再按請求區間過濾。`1d` 原樣回傳日 K；`1w`、`1mo` 以日資料計算週／月 OHLCV，然後回傳圖表。

週／月 K 的聚合方式為：開盤取該週／月第一筆日 K，最高取最高價，最低取最低價，收盤取最後一筆日 K，成交量加總。只要該期間有任一日資料缺少完整 OHLCV，彙整後的 OHLCV 就會是 `null`，避免把不完整資料畫成完整 K 線。

## 現有來源與用途

| 來源 | 用途 | 程式預設／狀態 | 權利與注意事項 |
|---|---|---|---|
| Fugle／時報資訊歷史 K 線 | 個股查詢缺口時回補日 OHLCV | `APP_MARKET_DATA_HISTORY_SOURCE=fugle` 才選用；預設設定是 FinMind。正式 API 曾回傳 Fugle 來源，使用者已表示供應商授權完成 | API 金鑰只由後端讀取。程式來源中記錄為 `CONFIRMED`；仍需依實際供應商合約／方案遵守展示、儲存、頻率和歸屬條件。端點限 Fugle 官方 HTTPS 主機，測試時允許 localhost。Fugle 每次請求區間必須短於一年，程式會拆段請求。 |
| FinMind `TaiwanStockPrice` | 替代的歷史日行情介接器 | `APP_MARKET_DATA_HISTORY_SOURCE=finmind`；若未設定即選用 | 程式目前將原始資料的公開展示／再散布權標為 `UNVERIFIED`。即使有 token，也不等於已取得公開展示權；回補服務會拒絕未確認來源，不能靠此來源完成正式站公開回補。 |
| TWSE「上市個股日成交資訊」CSV | 全市場最近一日快照，持續補入資料庫最新日線 | `APP_MARKET_DATA_INGESTION_ENABLED=true` 時啟動抓取並排程更新 | 來源標記為政府資料開放授權條款第 1 版，需依授權標示資料提供機關與 TWSE 原始來源。這是每日快照，不是可指定年份的完整歷史下載；它不會替尚未累積的多年資料做回溯。 |
| TWSE `STOCK_DAY` 月資料端點 | 舊的月資料 client／解析器 | 保留程式碼供既有測試；目前未註冊為預設公開歷史 provider | 不是現在正式公開圖表的回補來源。過去曾遇到 307 無 `Location` 的轉址問題；不要因存在 `APP_TWSE_MONTHLY_URL` 就認定它已接入按需資料庫回補。 |
| Fugle WebSocket trades | 本機原型的即時成交事件 | `/live` 只在 `local` profile 且啟用時允許；不是歷史回補 | 即時串流與歷史日 K 分離。事件不寫入日線資料庫；正式環境不對外開啟串流。 |

資料來源的型別及環境設定是兩個邊界：`APP_MARKET_DATA_PROVIDER=database` 選擇「從資料庫提供歷史行情」；`APP_MARKET_DATA_HISTORY_SOURCE` 選擇「缺口時向哪個歷史供應商回補」。對外部署目前應使用 database provider 與已確認可公開展示的 Fugle 歷史來源。

## 資料庫保存什麼

| 資料表 | 用途 | 重要內容 |
|---|---|---|
| `daily_market_bar` | 日 OHLCV 唯一事實來源（SSOT） | 標的、交易日、開高低收、成交量、原始價格調整政策、來源 ID、來源日期、首次取得／最後驗證時間、品質狀態。標的＋交易日＋調整政策有唯一限制。 |
| `market_history_coverage` | 記錄某標的哪些日期範圍已由來源成功查詢並匯入 | 標的、區間起迄、驗證時間。用來判斷是否仍有缺口，不代表每個自然日都是交易日或資料完整率已被證明。 |
| `market_data_source` | 來源與授權展示資訊 | provider、dataset、來源 URL、授權 URL、標示文字、授權狀態和最近資料日期。 |
| `market_data_ingestion_run` | 記錄快照／歷史匯入結果 | 起迄時間、來源、成功／失敗、收到／新增／更新／拒收筆數和錯誤碼。 |

歷史行情保存為未調整原始價格（`RAW`）。目前未套用股利、分割或其他公司行動調整。各資料表由 Spring JPA 目前的 `ddl-auto=update` 維護；本功能沒有引入 Flyway／Liquibase。

## 回補和資料完整性的界線

- 覆蓋紀錄表示上游對該請求區間成功回應且本地匯入成功；它不是交易所日曆，也不證明每個應有交易日都回傳了 K 棒。週末、休市、停牌、零成交和缺漏日原因目前無法由「沒有日 K」判別，因此 API 將 `coverageStatus` 標成 `UNKNOWN`。
- 上游成功回傳部分日線時，該請求區間會被記為已檢查；系統不會因為觀測列較少就自動知道某一天漏了。若上游資料日後修正，目前沒有 coverage TTL 或自動定期重驗機制。
- 空結果不寫 coverage，會回 `NO_MARKET_DATA`。後續再查同區間可能再次請求上游；這避免把暫時無資料永久快取成完整範圍，但對不支援的代碼可能重複失敗。
- 數據來源可能限制可查歷史長度、標的範圍、呼叫頻率或同時連線。憑證缺漏／錯誤、API quota、供應商故障或網路錯誤會讓回補失敗；已存日線仍可在 API 開啟且授權可展示時被讀取。
- 圖表最多查五年；若要看更久，需分段查詢或另做授權、容量與 API 限額設計。本系統目前不保證所有股票自 2010 年起都有完整歷史資料。
- API 以台北時區判斷「今日」，歷史 adapter 目前以 UTC 日期拒絕未來日期；台北跨日後、UTC 尚未跨日時，查詢包含台北今日的區間可能被上游 adapter 判為未來日期而失敗。
- 後端接受有效台股數字代碼不代表來源一定支援該證券；尚未上市、下市、特殊商品或來源沒有資料的代碼可能回傳錯誤或空結果。
- `APP_MARKET_DATA_ALLOW_UNVERIFIED_DISPLAY` 只影響資料庫讀取時是否允許顯示授權狀態未確認的舊資料；它不會使 FinMind 等來源取得授權，也不會讓回補服務接受未確認來源。正式環境應維持 `false`。

常見錯誤碼：

| 錯誤碼 | 意義 |
|---|---|
| `MARKET_HISTORY_DISABLED` | 圖表 API 被總開關關閉。 |
| `MARKET_DATA_LICENSE_UNVERIFIED` | 來源或回傳資料未確認公開展示權，拒絕回補／保存。 |
| `UPSTREAM_DATA_UNAVAILABLE` | 上游連線、憑證、回應格式、日期／OHLCV 驗證等失敗。 |
| `NO_MARKET_DATA` | 該標的與區間沒有可用 K 棒。 |
| `DATA_COVERAGE_INSUFFICIENT` | 僅用於舊的啟動回補器；筆數低於設定門檻。 |

## 可控制的參數

以下環境變數會覆寫 YAML 預設值。密碼、API key 和 token 應只放在 Zeabur／IntelliJ 的環境設定，不要提交進 Git，也不要輸出到 log。

| 環境變數 | 預設值 | 控制內容與建議 |
|---|---|---|
| `APP_MARKET_HISTORY_ENABLED` | `true`（application YAML） | 個股歷史圖表 API 總開關。關閉會回 `MARKET_HISTORY_DISABLED`。 |
| `APP_MARKET_DATA_PROVIDER` | `database` | 歷史資料提供邊界。公開部署維持 `database`，由資料庫讀取並在缺口時回補。 |
| `APP_MARKET_DATA_ON_DEMAND_BACKFILL_ENABLED` | `true` | 每次個股查詢是否自動補該標的／請求區間的 coverage gap。要讓使用者查新標的也能自動回補，需保持 `true`。 |
| `APP_MARKET_DATA_HISTORY_SOURCE` | `finmind` | 缺口回補來源，支援 `finmind`、`fugle` adapter。公開站應選擇已確認公開展示權的來源；目前已確認的部署選擇是 Fugle。 |
| `FUGLE_API_KEY` | 空 | Fugle 歷史及即時 provider 共用的伺服器端 key。歷史回補沒有 key 會失敗；不可放前端。 |
| `APP_FUGLE_HISTORICAL_URL` | `https://api.fugle.tw/marketdata/v1.0/stock/historical/candles` | Fugle 歷史日 K endpoint。正式環境應維持官方 HTTPS endpoint。 |
| `APP_FINMIND_URL` | `https://api.finmindtrade.com/api/v4/data` | FinMind 歷史 endpoint。 |
| `FINMIND_API_TOKEN` | 空 | FinMind API token；token 不等於原始資料公開展示／再散布授權。 |
| `APP_MARKET_DATA_ALLOW_UNVERIFIED_DISPLAY` | `false` | 是否允許讀出授權未確認來源的既有資料。公開環境維持 `false`；不應用它繞過授權。 |
| `APP_MARKET_DATA_INGESTION_ENABLED` | `true`（application YAML） | TWSE 全市場每日快照匯入。開啟後服務啟動時非同步抓一次，並按 cron 排程更新；這只補最新日資料，不回補多年歷史。 |
| `APP_GOVERNMENT_MARKET_DATA_URL` | `https://www.twse.com.tw/exchangeReport/STOCK_DAY_ALL?response=open_data` | 每日全市場 CSV snapshot URL。 |
| `APP_MARKET_DATA_REFRESH_CRON` | `0 20 18 * * MON-FRI` | 每日快照的 cron，時區固定 `Asia/Taipei`，預設平日 18:20。 |
| `APP_HISTORICAL_BACKFILL_ENABLED` | `false` | 舊的啟動式單一標的匯入器。啟用後每次服務啟動都回補同一支股票，不是使用者按需回補。日常正式部署應設 `false`；如使用舊名稱，`APP_FINMIND_BACKFILL_ENABLED` 是相容替代鍵。 |
| `APP_HISTORICAL_BACKFILL_SYMBOL` | `0050` | 舊啟動式匯入器的單一代碼；相容舊名稱 `APP_FINMIND_BACKFILL_SYMBOL`。 |
| `APP_HISTORICAL_BACKFILL_FROM` | `2010-01-01` | 舊啟動式匯入起日；相容舊名稱 `APP_FINMIND_BACKFILL_FROM`。 |
| `APP_HISTORICAL_BACKFILL_TO` | `today` | 舊啟動式匯入迄日；相容舊名稱 `APP_FINMIND_BACKFILL_TO`。 |
| `APP_HISTORICAL_BACKFILL_MINIMUM_ROWS` | `3500` | 舊啟動式匯入的最少筆數檢查；不影響一般使用者按需回補。 |
| `APP_LIVE_MARKET_DATA_ENABLED` | `false`（application YAML；local 為 `true`） | 即時行情串流開關。串流另有 local profile 限制，不控制歷史回補。 |
| `FUGLE_WS_URL` | `wss://api.fugle.tw/marketdata/v1.0/stock/streaming` | Fugle WebSocket endpoint；只供即時行情，不用於歷史 K 線回補。 |

## 建議的正式環境組態

```dotenv
APP_MARKET_HISTORY_ENABLED=true
APP_MARKET_DATA_PROVIDER=database
APP_MARKET_DATA_ON_DEMAND_BACKFILL_ENABLED=true
APP_MARKET_DATA_HISTORY_SOURCE=fugle
APP_MARKET_DATA_ALLOW_UNVERIFIED_DISPLAY=false
APP_MARKET_DATA_INGESTION_ENABLED=true
APP_HISTORICAL_BACKFILL_ENABLED=false
```

另外在 Zeabur 私密環境變數設定 `FUGLE_API_KEY` 和 Spring datasource 連線參數。不要將 key 貼進本文或 commit。`APP_MARKET_DATA_INGESTION_ENABLED=true` 可持續累積每日快照；即使關閉，已存資料和按需歷史回補也是分開控制的。

> 2026-10-10 Zeabur 運作紀錄顯示啟動回補器曾為 2330 執行 2010 年起的匯入，而 YAML 預設是關閉。這表示該部署有環境覆寫或其他啟動參數；該單一標的 runner 每次重啟都會再跑，日常部署建議關閉，只保留 `APP_MARKET_DATA_ON_DEMAND_BACKFILL_ENABLED=true`。

## 驗證紀錄

2026-10-10 在正式 UI 查詢 0051：1 年顯示 241 根日 K；切換五年後顯示 1,213 根日 K，觀察日期 2021-10-12 至 2026-10-08。重複呼叫同一 API 區間亦回傳相同的 `fetchedAt` 和 241 根資料，符合「成功回補後保存、重複查詢讀取已存資料」的行為。這是單一標的／區間的部署驗證，不是對全部代碼與所有供應商情況的完整性保證。

## 主要程式位置

- API 與日期／週期驗證：[`StockHistoryController.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/StockHistoryController.kt)、[`StockHistoryService.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/StockHistoryService.kt)
- DB-first、coverage gap 計算與按需回補：[`DatabaseHistoricalMarketDataProvider.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/DatabaseHistoricalMarketDataProvider.kt)
- 回補授權／回應驗證：[`MarketDataHistoryBackfill.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/MarketDataHistoryBackfill.kt)
- 持久化、來源／執行紀錄／coverage：[`JpaMarketDataStore.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/JpaMarketDataStore.kt)、[`MarketDataEntities.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/MarketDataEntities.kt)
- 歷史 adapter：[`FugleHistoricalMarketDataSource.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/FugleHistoricalMarketDataSource.kt)、[`FinMindHistoricalMarketDataSource.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/FinMindHistoricalMarketDataSource.kt)
- 每日全市場快照：[`GovernmentOpenDataDailySource.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/GovernmentOpenDataDailySource.kt)、[`MarketDataRefreshScheduler.kt`](../../backend/src/main/kotlin/uk/kuronekoli/strategylab/market/MarketDataRefreshScheduler.kt)
- 本機啟動方式與簡要設定：[`LOCAL_MARKET_DATA.md`](../../backend/LOCAL_MARKET_DATA.md)

## 參考連結

- [政府資料開放平臺：上市個股日成交資訊](https://data.gov.tw/dataset/11549)
- [政府資料開放授權條款第 1 版](https://data.gov.tw/license)
- [Fugle 歷史 K 線 API](https://developer.fugle.tw/docs/data/http-api/historical/candles/)
- [Fugle 資料授權說明](https://developer.fugle.tw/docs/data/intro/)
- [FinMind 台股技術資料](https://finmind.github.io/tutor/TaiwanMarket/Technical/)
