# 行情圖表資料來源與可替換架構方案

日期：2026-10-10
狀態：Stage 0–3 已實作並完成 Zeabur API 與 Chrome 瀏覽器驗收（2026-10-10）
決策目標：在保留現有個股圖表與 API 使用體驗的前提下，改用可授權、可追溯且可替換的資料來源；不把切換一個 feature flag 誤當作資料來源切換。

## 結論

圖表不應直接依賴某一家上游 API。採用「可替換來源 Adapter + 經驗證的日線資料庫 SSOT + 穩定的歷史行情 API」：來源負責取得/匯入資料，canonical store 負責保存本站目前採用的日線，個股研究、圖表、週/月 K 聚合與回測都讀同一份日線事實。

政府資料開放平臺的「上市個股日成交資訊」可作為候選日線來源。資料集欄位包含日期、證券代號、名稱、成交股數、開高低收、漲跌價差及成交筆數，平台標示每日更新與政府資料開放授權第 1 版。授權允許重製、散布、公開傳輸及衍生利用，但必須依條款清楚標示來源。資料集資料資源目前應視為每日更新快照；政府平台上的歷史資料需求回饋也指出，公開資料只能提取更新後的單筆/當期資訊，未提供指定日期範圍的日資料歷史查詢。因此，此來源可供未來逐日累積歷史，不能單獨回補 2010–2026。

這代表現有畫面可以保留，但範圍要依本站已匯入的資料覆蓋呈現。已累積的日線可畫日 K/收盤走勢，週 K、月 K 繼續由日線確定性聚合；未累積的早期日期不能假裝有資料。完整回測長期區間需要另一個具明確歷史取得、儲存與展示權的 provider 或合法歷史檔案匯入。

## 已核對的專案現況

- Angular 個股研究頁已提供走勢/K 線、日/週/月週期、成交量、MA20/MA60 與日期 OHLCV；讀取 `GET /api/v1/stocks/{symbol}/history`，不直接連接 TWSE。
- 該 API 回應已有 `source`、`interval`、授權狀態、價格調整政策、bars 與 limitations，可作為新來源的相容邊界；目前尚未加入 DB-backed 日線 store。
- 後端已有 `HistoricalMarketDataProvider` 介面，但 `StockHistoryService`、回測與 robustness 目前都直接注入此上游 provider。可用它保留來源取得邊界，但應再建立 persistence port，避免把外部來源誤當本站歷史 SSOT。
- `TwseMarketDataClient` 直接按月呼叫 TWSE `STOCK_DAY`。`app.market-history.enabled` 只控制歷史 API 是否開放，並不選擇或切換 provider。
- 正式設定 `APP_MARKET_HISTORY_ENABLED` 預設為 `false`；local profile 預設為 `true`。這個 local 設定不代表正式部署資料權已確認。
- 即時行情 SSE 是另一條獨立路徑，並非本方案目標；此處只規劃每日 OHLCV 歷史圖表。

## 目標元件與責任

```text
來源端（每日快照／授權歷史供應商）
       │ adapter: fetch + parse + source attribution
       ▼
MarketDataIngestionService ── 驗證/品質檢查 ──► MarketDataStore (port)
                                                       │
                                              relational DB (SSOT)
                                                       │
                                                       ▼
Chart/Backtest application services ──► existing history API contract ──► Angular UI
```

1. **Source adapter**：每個供應商一個 adapter，負責 HTTP/檔案格式、解析、來源識別、更新日期與原始錯誤分類。adapter 不寫 JPA，也不讓控制器或 Angular 知道供應商 URL。
2. **Ingestion service**：驗證標的映射、日期、數字格式、OHLC 關係、重複資料及來源更新時間，再以單一交易提交已驗證批次。錯誤/空資料不得刪除 last-good。
3. **MarketDataStore port**：依市場、標的、日期範圍讀取及冪等 upsert 原始日線。JPA adapter 是一種實作；服務層不依賴 MySQL 專屬查詢。SQLite/雲端 DB 需各自驗證 schema、upsert、索引和交易語意。
4. **History query service**：從 store 讀取原始日線，產生既有 API 回應與日/週/月 bars。週/月仍為日線衍生視圖，不另存可寫入的第二份真相。
5. **Source registry/configuration**：選擇一個 canonical 歷史資料匯入來源及明確授權狀態。切換來源須經核對/匯入，不在每次使用者查詢時隨機 fallback 到另一家供應商。
6. **Attribution metadata**：回應及畫面可辨識來源名稱、資料集連結、資料日期與更新時間；資料衍生後仍依 OGL 標示來源。不要把來源機關表示成背書或推薦。

## 可替換介面建議

來源與資料庫是兩個不同的抽換點：

```kotlin
interface DailyMarketDataSource {
    val sourceId: String
    fun fetchLatestSnapshot(): SourceSnapshot
    fun fetchHistory(symbol: String, from: LocalDate, to: LocalDate): SourceSnapshot
}

interface MarketDataStore {
    fun findDailyBars(instrumentId: InstrumentId, from: LocalDate, to: LocalDate): List<StoredDailyBar>
    fun upsertValidated(batch: ValidatedDailyBarBatch): IngestionResult
}
```

實際方法可依批次規模調整；介面要表達來源快照/lineage 和已驗證批次，不要只回傳裸 `List<DailyBar>` 而遺失來源資訊。現有 `HistoricalMarketDataProvider` 可暫時由 DB-backed adapter 實作以減少回測重構面積，後續再把命名與責任調整為 `DailyMarketBarQuery`；不要求同一 PR 全面重寫所有服務。

建議環境設定分開表達不同目的：

```yaml
app:
  market-history:
    enabled: false                 # 是否對外提供歷史圖表 API，正式環境安全預設
  market-data:
    canonical-source: government-open-data-daily
    ingestion-enabled: false       # 是否執行排程匯入，單獨控制
    source-license-status: UNKNOWN # 僅在授權/來源審核完成後改為 VERIFIED
```

設定名稱只是提案。真正的啟用規則應由設定驗證或啟動檢查強制：歷史 API 開啟時，必須有 DB store、被選定來源、有效的授權審核狀態、來源歸屬欄位，且 API 不可直接退回目前未核實的 TWSE 月端點。`APP_MARKET_HISTORY_ENABLED=true` 單獨不能解除來源檢查。

## SSOT 與建議資料模型

沿用並細化 [`MARKET_DATA_STORAGE_SSOT.md`](MARKET_DATA_STORAGE_SSOT.md) 的概念模型；先以每日原始日線為唯一 canonical 歷史事實：

- `instrument`：穩定的市場 + 代號識別，不以股票名稱當 key。
- `daily_market_bar`：唯一鍵 `(instrument_id, trading_date, adjustment_policy)`；原始 OHLC 用 DECIMAL、成交股數用 BIGINT；保存 `source_id`、`source_record_date`/`source_as_of`、`first_fetched_at`、`last_verified_at`、`quality_status`。
- `market_data_source`：來源識別、資料集/授權連結、歸屬文字、允許用途審核狀態與審核日期。若授權條款不能僅以一個 status 表達，保留文件化的審核記錄與有效範圍。
- `market_data_ingestion_run`：起訖日期、來源、成功/部分/失敗狀態、收到/新增/更新/拒收數量及安全錯誤碼。

同一來源修正既有日期時，以交易式 upsert 更新 canonical row 並記錄匯入 run。來源切換不得把兩個來源的欄位拼成單根 K 線，也不可靜默覆蓋；先比較重疊區間、處理修正/差異，再明確遷移 canonical source。若未來需要來源並存及比價，另建 provider observation/staging 表，canonical 表仍每標的/日期/調整政策只有一筆有效資料。

## 首選來源的資料界線

| 能力 | 政府開放資料「上市個股日成交資訊」候選 | 對產品的影響 |
| --- | --- | --- |
| 日資料欄位 | 日期、代號/名稱、成交量與金額、開高低收、漲跌、成交筆數 | 欄位足以建構日 K/走勢/成交量，不需改前端圖表資料形狀 |
| 授權 | 資料集列 OGL v1；可重製、散布、公開傳輸及衍生利用，需明確來源標示 | 來源名稱、資料集、授權版本與歸屬應在資料 lineage 及「資料與方法」頁呈現 |
| 更新 | 每日更新 | 預期可每日匯入，UI 應標明最後資料日期與擷取時間；非即時 |
| 歷史範圍 | 平台的歷史查詢回饋指出目前只提供更新後單筆/當期資料 | 必須持續累積，無法單獨支援 2010–2026 回補；新標的可用期間受本站開始收集日期限制 |
| 指數/即時/分鐘資料 | 不由此個股日資料集提供 | 首頁盤中指數、tick、分鐘線、WebSocket 不在此來源的承諾範圍 |

開發前需再次檢查資料集的當前資源 URL、CSV/API 內容、每日檔案行為、交易日涵蓋範圍、代號型別、ETF 是否涵蓋與授權文字，因平台資源及 endpoint 可能調整。只有成功解析且符合品質規則的資料才能進 canonical store。

## UI/API 維持策略

- 不改 `GET /api/v1/stocks/{symbol}/history?from&to&interval` 的既有路由及 bars 欄位；日/週/月、走勢/K 線、成交量、均線和 OHLCV 檢視繼續由相同 API 驅動。
- 回應保留 `source`、`licensingStatus`、`adjustmentPolicy`、`observedFrom`、`observedTo`、`fetchedAt`、`limitations`；建議新增明確 `lastVerifiedAt`、`freshnessStatus` 與 `coverageStatus`，以可選欄位向後相容。
- 查詢區間超過本站已保存資料時，不得默默呼叫未審核來源。可採擇一契約：回傳已覆蓋範圍並標示部分資料，或以明確的 `DATA_RANGE_UNAVAILABLE` 回應及最早可用日期。需先決定是否允許部分結果，不能讓使用者誤以為是完整長期圖表。
- UI 資料標籤改成可讀歸屬，例如「資料來源：臺灣證券交易所，上市個股日成交資訊（政府資料開放授權第 1 版）；資料截至 YYYY-MM-DD」。同時標示「歷史日資料／非即時」、「本站可用期間」與未調整價格。歸屬不放在只對開發者可見的 tooltip。
- 空資料、落後、部分覆蓋、來源故障各有不同狀態；來源短暫故障時若 store 有 last-good，照常顯示並明確標示資料日期，不把 stale 內容說成最新。
- 回測只接受通過 coverage/品質要求的完整區間；不得把圖表可顯示的部分資料自動當作完整回測資料。

## 逐階段實作與驗收

### Stage 0：來源確認與授權記錄（實作前）

- 確認政府資料開放平臺資料集及連結資源當前仍有 OHLCV；保存精確 dataset/resource URL、擷取日期和 schema 範例。
- 落實 OGL v1 歸屬聲明與顯示位置；確認本站保存、聚合、回測使用及公開展示均在該資料集授權範圍內。這不是對其他 TWSE endpoint 的概括授權。
- 量測更新頻率、資料可用時間、是否為全市場每日快照、歷史能否下載，以及資料代號與現有 catalog 的映射率。
- 記錄來源審核結果；若資料資源/授權不符或無法穩定取得，維持正式行情 API 關閉並選擇其他 provider。

### Stage 1：Source adapter + SSOT ingestion

- 新增 government open-data adapter，使用可設定 URL、逾時、大小限制、重試/退避及安全日誌；不記錄整份回應或任何秘密。
- 解析/映射為標準化日線；以合成固定 fixture 測試欄位順序變更、千分位、缺值、無成交特殊值、日期/時區、UTF-8 與錯誤列。未知格式要整批拒絕或隔離，不猜欄位。
- 新增 repository port 與 JPA store、run 記錄；驗證重複匯入冪等、修正 upsert、錯誤不覆蓋 last-good、多標的批次交易及 MySQL/H2 schema。
- 建立可重跑的每日排程及手動受控補跑入口；使用 single-flight/分散式鎖或明確單實例部署假設。只保留資料集可提供的日期，不推測未觀察交易日。

### Stage 2：改讀 DB 並保留圖表契約

- `HistoricalMarketDataProvider`/history query 改為讀 `MarketDataStore`；在本地和測試使用 fixture，驗證 Angular 不需變更即可收到相同 bars contract。
- 日線作 SSOT；週/月照既有規則由日線聚合，並測試跨月/跨年、部分週月、修正後聚合、OHLCV 完整性。
- 明確 earliest available date、coverage/freshness 與來源歸屬；超出範圍時回應狀態符合 Stage 0 決策。
- 對回測/robustness 加入資料覆蓋檢查；報告輸出來源和 dataset fingerprint/資料水位，避免不同來源結果不可解釋。

### Stage 3：正式開放圖表

- 只有前述授權、資料品質與來源健康檢查通過後，才在正式環境選定 provider、開啟 ingestion，並將 `app.market-history.enabled` 設為 true。
- 開啟前在 Zeabur 上確認資料庫連線、遷移完成、最近一次匯入成功、0050 有可查 OHLCV、來源歸屬可見、API 的資料日期/區間正確，再以 smoke test 驗證圖表 API。
- 任何 gate 不成立時，保留安全預設 false；不要只設 true 讓 API 從使用者請求直接打未審核的 TWSE 月端點。

## 驗收條件

1. 來源可以替換而不修改 Angular 圖表邏輯；history API contract 有相容性測試。
2. 0050 的一個已匯入期間能產生完整 OHLC 日線，週/月 K 由同一 canonical 日線聚合。
3. 重複匯入不增加重複列；來源修正可追蹤並更新；來源失敗不清除最後成功資料。
4. UI 顯示來源、授權/歸屬、最後資料日期、非即時狀態和可用歷史期間；超範圍不偽裝成完整結果。
5. 回測只使用通過完整性策略的範圍，且結果帶來源/資料水位。
6. 正式開關預設關閉，打開前有機械化設定檢查與實際資料 smoke test；本機測試不被描述成 Zeabur 驗證。
7. 沒有 TWSE 月端點時，完整長歷史仍需另一個合規 provider/資料檔；此限制在 UI 和 roadmap 均清楚可見。

## 主要風險與替代方案

- **只累積不回補**：最早日期會隨運行時間逐步向前。面試版可明確展示真實可用期間；若必須展示 2010 起長圖，需向提供歷史 OHLCV 與再利用授權的來源取得歷史檔/API。
- **每日資料未必準時或穩定**：排程要記錄 freshness；故障用 last-good 並標 stale，不能以空快照清庫。
- **上游 schema 變更**：adapter fixture、schema fingerprint/欄位檢查與失敗告警；不要讓上游欄位變動 silently shift OHLC。
- **來源切換的數值差異**：不同來源對調整價、成交量、日期或更正的定義可能不同。不同 `adjustment_policy` 和來源不能混列，切換時保存比較報告及資料水位。
- **候選替代來源**：政府開放資料是第一候選；另一合法歷史供應商可實作為新的 adapter。只有需要即時資料時才另設 LiveQuote provider 與更新契約；它不取代歷史 SSOT，也不要求本階段導入 WebSocket。

## 來源

- [政府資料開放平臺：上市個股日成交資訊（資料集 11549）](https://data.gov.tw/dataset/11549)
- [政府資料開放授權條款第 1 版](https://data.gov.tw/license)
- [政府資料開放平臺：上市與上櫃個股日成交資訊歷史查詢回饋](https://data.gov.tw/suggests/136936)
- [TWSE 使用條款](https://www.twse.com.tw/zh/terms/use.html)
- 本 repo [`MARKET_DATA_STORAGE_SSOT.md`](MARKET_DATA_STORAGE_SSOT.md) 與現有 `HistoricalMarketDataProvider`、`StockHistoryService`、`TwseMarketDataClient`、Angular `stock-research` 個股研究介面。

## 實作紀錄（2026-10-10）

- **Stage 0 完成（來源範圍）**：核對資料集 11549 的官方資源為 `https://www.twse.com.tw/exchangeReport/STOCK_DAY_ALL?response=open_data`；當日實際取回 UTF-8 CSV，1,380 列、單一日期 2026-10-08，0050 有完整 OHLCV。來源 CSV 需要合理 User-Agent。OGL v1 與歸屬文字已加入 API/圖表說明。這是資料集授權範圍內的快照，不代表 TWSE 月歷史端點也可公開使用。
- **Stage 1 完成（adapter / SSOT）**：加入嚴格 CSV parser、來源/日線/匯入 run JPA entities、`MarketDataStore` port、交易式冪等 upsert、來源切換衝突防護、排程與啟動匯入。schema 不變但 OHLC 空白的列（不能形成有效 K 線）會明確略過並計入 rejected rows；欄位結構或 OHLC 關係錯誤仍整批拒收並保留 last-good；不在資料庫交易中呼叫上游。
- **Stage 2 完成（讀取 / UI 契約）**：預設 historical provider 改讀 DB；查圖不再呼叫月端點。既有 API bars 欄位與日/週/月計算保留，新增可選來源、授權 URL、最早資料日及最後驗證時間。Angular 可顯示單筆日 K，明確提示資料自開始收集日起累積；回測/穩健性會拒絕早於本站最早可用行情的請求。
- **Stage 3 完成（Zeabur / UI 驗收）**：Zeabur 啟動 log 確認 MySQL pool 成功，並記錄 2026-10-08 快照 `received=1380, inserted=1364, rejectedNoOhlc=16`。正式 0050 日/週/月 history API 均回 HTTP 200；日資料包含 OHLCV（115.35/115.55/114.90/114.95，成交股數 99,877,876），回應標示來源、授權、非即時限制與可用期間。實際 Chrome 開啟部署頁 `https://www.kuronekoli.uk/twse-strategy-lab/#/stocks/0050`，確認收盤走勢、K 線及日/週/月週期可見，來源歸屬與截至日期可讀。此時只有一個交易日，因此週/月 K 目前各由同一日資料聚合；這是部署圖表功能驗收，不代表多年歷史覆蓋。

### 實際限制

官方資料集/授權允許使用所列開放資料並依條款標示，但歷史回饋及已取回資源均顯示它不是可查任意日期的歷史 API。資料庫從部署日起逐日形成序列；若產品需要 2010–2026，仍需單獨取得有回補、儲存、衍生及公開展示權的歷史供應商資料。沒有用合成資料填補真實市場圖，也沒有把單日 K 稱為完整走勢。
