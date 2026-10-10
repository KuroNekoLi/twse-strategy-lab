# 行情資料持久化與 SSOT 設計

日期：2026-10-10
狀態：部分實作（2026-10-10）；daily bar/source/ingestion-run 資料表、政府資料來源匯入與 DB-backed history 已落地。週／月 K 仍由日線查詢時計算。須持續收集日資料；無歷史回補。
範圍：先支援台股歷史日線與其衍生圖表，維持資料來源可替換，並避免每次查圖都呼叫上游。

## 決策摘要

採「資料庫中的已驗證日線是本站歷史行情的唯一 canonical store（SSOT）」：供應商只負責匯入／校正資料；個股圖表、回測、走勢與週／月彙總都讀同一份日線事實。週 K、月 K 第一階段由日 K 聚合，不另外成為一份可獨立修改的行情真相。

已落庫不等於永遠不變。歷史行情通常比即時報價穩定，但上游可能補資料或修正資料；每筆行情要能標示來源與最後驗證時間，匯入採 upsert，來源暫時失敗時保留上次驗證成功的資料並明確標示資料新鮮度。取得及儲存行情、衍生圖表展示的授權仍須確認；本提案不把公開 API 等同於可任意永久保存或公開再散布。

## 目前程式現況

- `catalog_instrument` 與 `catalog_source` 已透過 JPA 持久化標的目錄；目錄每日排程刷新，失敗時保留上次成功目錄。
- `StockHistoryService` 經 `HistoricalMarketDataProvider` query 邊界讀取資料；正式預設 adapter 讀取 JPA `MarketDataStore`，匯入由獨立 `DailyMarketDataSource` 負責。
- `GovernmentOpenDataDailySource` 讀取資料集 11549 的全市場日 CSV 快照；TWSE `STOCK_DAY` client 不再為預設 provider，也不可在未另行授權下用於公開部署。
- `StockHistoryService` 支援 `1d`、`1w`、`1mo`；週／月由當次取得的日線即時計算，未還原，公司行動與授權狀態尚未確認。
- 前端個股圖表可切換日／週／月，均線的「20／60」依目前 bar 數計算，代表所選週期的 20／60 根，不是固定交易日視窗。
- 0050 日線會按 `(symbol, trading_date)` 存入 DB，API 查圖不呼叫行情來源；排程匯入每日快照並記錄 lineage/ingestion run。
- 週界目前採 ISO Mon–Sun，月界採曆月；回應列出期別邊界、實際觀測範圍、是否被查詢日期截短及期間狀態。因未接交易日曆／完整性資料，`coverageStatus` 一律是 `UNKNOWN`；觀測首末日不代表中間資料齊全。

## SSOT 邊界

| 資料 | 建議權威來源／SSOT | 變動性 | 儲存策略 |
| --- | --- | --- | --- |
| 標的識別碼與市場 | 本站 canonical instrument identity；外部目錄是匯入來源 | 低，但市場、種類與上市狀態可能變動 | 長期存檔；身份欄位穩定，名稱／狀態保留更新時間或有效期間，不把整筆目錄當永不變資料 |
| 標的名稱與目錄屬性 | 經驗證的目錄快照及來源記錄 | 偶爾變動 | 沿用現有 `catalog_*` 持久化；後續若需查歷史名稱再加版本／有效期間，不急著重複存多份快照 |
| 原始日 OHLCV | 選定且授權明確的市場資料 provider，匯入本站 DB 後的 canonical 日線表 | 低頻修正、補值或更正仍可能發生 | 按標的與交易日 upsert；保存來源、取得／驗證時間與品質狀態。單一 canonical provider policy 下，每個標的／日期只有一筆目前有效原始日線 |
| 週 K、月 K | canonical 日線的確定性聚合結果 | 日線修正時會連動改變 | 第一階段查詢時聚合；以最後納入的日線版本辨識結果。只有量測顯示 DB 即時聚合成本過高時才快取，且必須可由日線重建 |
| 還原價格與股利／公司行動 | 獨立且經驗證的公司行動資料源及明確算法版本 | 可能更正，且算法會演進 | 不覆寫原始 OHLCV；公司行動作獨立事實，還原序列是具版本的衍生資料，不能與未還原價格混成同一欄位 |
| 即時報價／tick | 有展示與儲存權的即時供應商 | 秒級／盤中快速變動 | 不進長期日線 SSOT；短 TTL 快取或明確授權後的獨立 tick 歷史庫，設定容量與保留期限 |
| 使用者觀察清單／研究筆記 | 使用者自身的資料 | 使用者可隨時修改 | 目前沒有登入／使用者隔離，觀察清單留在瀏覽器本機；不可寫成全站共用 DB 資料。建立身份與授權模型後再遷移 |
| 回測輸出 | 回測輸入、引擎版本與行情版本共同定義 | 計算輸出可重現，但輸入／引擎更新會改變 | 需要保存可重現回測時另存請求參數、引擎版本、日線版本／資料水位與結果；不可只存沒有 lineage 的績效數字 |

首頁市場概覽（主要指數、漲跌家數、產業漲跌、熱門排行）是另一個資料域，不能從單一 0050 日線推導。若首頁要顯示歷史收盤摘要，可依已授權的市場級來源新增按 `market + trading_date + metric/symbol` 唯一的 `market_overview_snapshot`，保留 `source_id`、`source_as_of`、`fetched_at`、`quality_status`；若需要可回溯的指數迷你走勢，存指數日線至同一個 market-bar model（以 instrument identity 區分指數與個股）。產業分類與成分會變動，需有來源及有效日期；不能把今日分類套到過去資料。

盤中指數卡、即時熱門／爆量排行屬高變動 feed，獨立標示更新時間與來源，採短 TTL 或經授權的 snapshot retention；不可跟歷史日 K 共用 freshness policy。排名值是特定來源與時點的觀察，不是穩定的標的屬性，也不應被回測當成歷史事實，除非來源提供可驗證的完整歷史序列。

## 建議的資料表

以下是概念模型，不代表已執行 migration。使用 JPA repository/port 隔離資料存取，避免 Service 依賴 MySQL 專屬 SQL；欄位採標準日期、整數與定點數型別。未來使用 SQLite 或雲端資料庫仍需驗證 dialect、索引與交易語意，不能只換 JDBC URL 就宣稱相容。

### `instrument`

本站的標的 canonical identity，主鍵使用穩定的 `market + symbol`（例如 `TWSE:0050`），不以公司名稱當 key。

| 欄位 | 意義 |
| --- | --- |
| `instrument_id` | 主鍵，例如 `TWSE:0050` |
| `market`、`symbol` | 市場及原始代碼；唯一索引 |
| `kind` | STOCK、ETF 等已驗證種類 |
| `current_name` | 目前顯示名稱，來自目錄來源 |
| `listing_status` | ACTIVE、SUSPENDED、DELISTED、UNKNOWN；未知不得猜測 |
| `first_seen_at`、`last_seen_at`、`updated_at` | 本站觀測時間 |
| `catalog_as_of`、`catalog_source_id` | 目錄出表日期與來源 lineage |

現有目錄按來源保存，同一代碼可有不同來源列。建立 canonical identity 時要先定義來源優先序及代碼衝突規則，再讓目錄列關聯至 identity，不可直接假設 `catalog_instrument.id` 就是跨來源唯一標的。

### `daily_market_bar`

不可變粒度：一個市場標的的一個交易日、一套價格調整政策。

| 欄位 | 意義 |
| --- | --- |
| `instrument_id`、`trading_date` | 複合唯一鍵，分別對應標的與交易日 |
| `open`、`high`、`low`、`close` | 原始日 OHLC，使用 DECIMAL；不得用浮點數 |
| `volume` | 成交股數，BIGINT；未知為 NULL，不以 0 代替 |
| `adjustment_policy` | 初期固定 `RAW`；不允許 raw/adjusted 共用同一列 |
| `source_id`、`source_as_of` | 供應商與來源資料日期／版本 |
| `first_fetched_at`、`last_verified_at` | 首次取得和最近一次驗證成功時間 |
| `quality_status` | VALID、INCOMPLETE、QUARANTINED 等明確品質狀態 |

查詢索引以 `(instrument_id, trading_date)` 為主。OHLCV 完整性與價格關係（`low <= open/close <= high`）、日期唯一性及來源回應狀態在寫入前驗證。對同一日期的修正採 transaction upsert，並記錄匯入執行；若未來需要可稽核的完整修正歷史，再新增版本表，不在第一版無條件複製每次讀取。

### `market_data_ingestion_run`

記錄每輪資料匯入，讓快取新鮮度、供應商故障與補抓進度可查。

| 欄位 | 意義 |
| --- | --- |
| `run_id`、`source_id` | 執行識別及 provider |
| `started_at`、`finished_at` | 執行時間 |
| `range_from`、`range_to` | 本次預期涵蓋的日期範圍 |
| `status` | RUNNING、SUCCEEDED、PARTIAL、FAILED |
| `rows_received`、`rows_inserted`、`rows_updated`、`rows_rejected` | 可核對的筆數 |
| `error_code` | 不含憑證或原始個資的錯誤分類 |

若一次 run 同時涵蓋大量標的／月份，可再拆 `market_data_ingestion_batch` 以記錄每個標的與月份的狀態；在尚未確定批次規模前不先過度拆表。

## 日線匯入與 API 流程

1. 選定並設定具備儲存及展示權的 provider adapter。`DailyMarketDataSource` 負責匯入，`HistoricalMarketDataProvider` 負責讀取；資料庫 store 另以介面抽象，Service 不直接依賴 TWSE URL、JPA 或特定 DB。
2. 首次有需求時進行有上限的回補，按標的／月份取得上游資料；完整驗證某批資料後再交易式 upsert。失敗或空白月份不刪除舊資料，也不寫成「零筆行情」。
3. 日常排程只補最近未完成交易日及最近月份；另以低頻重查窗口捕捉供應商更正。排程需有 single-flight／鎖，避免多個應用實例重複打上游。
4. 個股圖表及回測先查 canonical 日線庫。只有日期覆蓋不足或資料超過 freshness policy 時才觸發補抓；請求路徑應避免對同標的／月份同時重複呼叫 provider。
5. 若上游故障但 DB 已有完整覆蓋，回傳資料時暴露 `lastVerifiedAt`／freshness 狀態；若資料缺範圍，明確回報不完整或暫時不可用。不得把舊資料偽裝成即時資料，也不得默默用零值填補交易日。
6. 既有 `GET /api/v1/stocks/{symbol}/history?from&to` 可維持回應形狀；逐步改由 DB 提供 bars。是否加 `interval=1d|1w|1mo` 要等聚合邊界、授權與前端操作決定後再做契約變更。

## 週 K／月 K 聚合規則

週／月 K 由實際收到的日線觀測聚合，不補出沒有觀測的日期。當前可用週界是 ISO Mon–Sun，並未以台灣交易日曆驗證週首末日：

- 開盤：期間內第一筆有效日線的 `open`。
- 最高／最低：期間內有效日線 `high` 的最大值及 `low` 的最小值。
- 收盤：期間內最後一筆有效日線的 `close`。
- 成交量：已知日線 volume 的總和；任一必要輸入未知時標示不完整，不可當作完整總量。
- 週 K 以 ISO 星期一至星期日作為輸出期間；月 K 以曆月為區間。這是目前 API 的分桶規則，交易日完整性仍未知。
- 部分目前週／月需要標記為未完成期間；停牌、缺資料和下市前最後一筆資料不能靠補值掩蓋。
- 日線被修正時，週／月輸出隨之更新。首版直接由日線聚合，避免日、週、月三份權威互相漂移。

## 避免重複請求與失效資料的政策

- 資料表解決「每次查圖都重抓全部月份」；它不代表可消除所有上游請求。仍需排程檢查最新資料和更正。
- 依資料類型定不同 freshness：交易日收盤後以最近完整交易日為目標；非交易日不應判為缺資料。目錄沿用目前來源 TTL／每日排程概念。即時 quote 使用秒級獨立 TTL。
- 伺服器回應提供 `observedFrom`、`observedTo`、`fetchedAt`、`lastVerifiedAt`、source 與 freshness/quality 狀態；UI 用文字說明歷史收盤／資料延遲。
- 來源授權為 UNKNOWN 或到期時，停止公開提供資料；是否允許內部快取也要依條款決定。
- 不用 Redis 作行情 SSOT。若日後為降低熱門查詢 DB 負載而加 Redis，僅作可刪除的短 TTL read-through cache，DB 仍為唯一 canonical store。

## 演進階段與驗收

### Phase 0：確認資料與權利

- 確認歷史 OHLCV 的取得、持久保存、衍生聚合與網站展示權。
- 選定 canonical provider 與來源優先順序；界定缺漏／停牌狀態、收盤日 freshness 和更正窗口。
- 決定 0050 回補期間及上游節流／排程限制。未確認權利前不得啟動正式批次回補或公開展示。

### Phase 1：持久化原始日線

- 新增 instrument identity、daily bar、ingestion run 對應的 JPA model/repository 與 provider-neutral store port。
- Local/H2 測試首次匯入、重複匯入冪等、修正 upsert、來源失敗保留 last-good、OHLCV 驗證與範圍查詢。
- 0050 的同一個歷史區間第二次查詢不再呼叫上游；資料日期與來源 lineage 一致。
- 舊 API contract 保持相容；回測與圖表讀相同日線 SSOT。

### Phase 2：週／月 K 與圖表導覽（部分完成）

- 先由日線聚合週、月 bars，測試跨年、週界、月界、不完整週月、缺值和日線修正。
- API 回應明確 `interval`、來源、未調整政策與 freshness；前端加入日／週／月控制和所選期間 OHLCV。
- 已交付：`interval=1d|1w|1mo`、日線衍生週／月 bars、期間與觀測日期 metadata、前端切換和依完整 OHLC 顯示 K 線。此切片的 coverage 明確未知，不能視為 Phase 2 全部驗收完成。
- 只有在真實 workload 證明動態聚合不夠時，才增設可重建的衍生快取／聚合表。

### Phase 3：調整後價格與回測 lineage

- 先有正式公司行動資料及算法測試，保留 raw daily bars 作永不混寫的基礎資料。
- 保存回測所用策略／引擎版本及行情資料水位，讓歷史結果可說明、可重現。

## 目前阻塞與不納入首版

- 政府開放資料集 11549 的日快照及 OGL v1 來源標示路徑已確認；但多年歷史回補、交易日完整性、公司行動與完整回測 coverage 仍未知。任何其他來源的儲存/展示權均需個別確認。
- 日線供應目前曾遇到 TWSE 回應 307 且缺少 Location 的錯誤；持久化只能降低已取得資料重複請求，無法修復首次取得失敗。匯入應可重試並保留 last-good。
- 週／月 K API 與前端切換已實作，現在讀取 canonical DB 日線後請求時計算；仍沒有市場日曆、完整性判定與歷史回補。
- 還原價格、法人、財報、新聞、分鐘線與即時 tick 不在 Phase 1；各自需要資料源、權利、更新與品質規則。
- SQLite 與不同雲端資料庫的相容性尚未測試；JPA/Repository 可隔離大部分 persistence code，但 schema migration、索引、decimal、upsert、鎖與測試矩陣仍須逐一驗證。

## 實作前核對清單

- [ ] 行情保存、衍生計算和網站展示權已確認並記錄 provider、條款與日期。
- [ ] canonical market/symbol identity、provider precedence、source revision 與 correction policy 已確定。
- [ ] 交易日曆與週界規則已有可測試實作。
- [ ] 冪等 upsert、last-good、部分資料、重試及多實例排程鎖已有測試。
- [ ] 資料庫 schema/migration 對本機 H2、部署 MySQL 及目標替代資料庫完成驗證。
- [ ] 圖表和回測讀同一份 canonical 日線，並展示資料日期、來源、調整政策及 freshness。
