# M0 市場資料規格與現況盤點

文件版本：`M0-DATA-2`。查核日期：2026-10-09（Asia/Taipei）。負責執行：`/root/m0_data`，Data Engineer / Technical Writer。

本文件服務 EPIC-01，依 `產品規劃.md` 第 8、9、13、21 節盤點欄位、來源、品質與可重現限制。M0 完成條件是可追溯的規格與缺口清單；完整歷史資料管線、公司行動帳務、含息報酬和公開發布皆不因本文件而取得完成或授權狀態。資料使用權另見 [DATA_LICENSING_MATRIX.md](DATA_LICENSING_MATRIX.md)。

## 1. 證據類型與支援邊界

| 標記 | 意義 |
|---|---|
| `OBSERVED_CODE` | 已讀取專案程式確認；不等同啟動後的執行證據 |
| `OBSERVED_SOURCE` | 本次讀取官方網頁、官方規格或單次來源回應 |
| `PROPOSED` | 下列資料模型、快照與閘門的規格，尚未因此實作 |
| `UNKNOWN` | 證據不足，不能以預設值推定 |
| `BLOCKED` | 必要條件未符合，不能標為可公開的可信結果 |

資料路徑為 [application.yml](../../backend/src/main/resources/application.yml) 的 `app.twse.base-url` → [TwseMarketDataClient](../../backend/src/main/java/uk/kuronekoli/strategylab/market/TwseMarketDataClient.java) 的 `load/loadMonth/parseMonthResponse` → [DailyBar](../../backend/src/main/java/uk/kuronekoli/strategylab/market/DailyBar.java) → [BacktestService](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestService.java) 的 `run` → [BacktestEngine](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestEngine.java) → [BacktestResponse](../../backend/src/main/java/uk/kuronekoli/strategylab/api/BacktestResponse.java)。[BacktestValidation](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestValidation.java) 共用資料檢查，[BacktestFingerprint](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestFingerprint.java) 建立識別雜湊。以下是 M0 修改後的程式觀察；修改前基準保留於第 3、4 節，不代表目前行為。

| 能力 | M0 目前程式觀察（`OBSERVED_CODE`） | 資料結論 |
|---|---|---|
| 歷史日行情 | 每標的每月呼叫 `STOCK_DAY`；每月及串接後要求日期嚴格遞增，不再排序掩蓋來源亂序 | 存在來源讀取及防護程式；不代表完整期間或授權已驗證 |
| 收盤價 | `DailyBar(LocalDate date, BigDecimal close)`，價格字串直接轉十進位並檢查正值、範圍與精度 | 可用於有明確限制的日收盤研究模型，不能推定真實成交 |
| OHLCV | 上游欄位存在，目前僅使用日期和收盤價 | 未保存／未傳入引擎，不支援依盤中高低價或開盤價成交 |
| 交易日曆 | 無日曆資料來源或版本 | 無法判斷缺列是休市、停牌、無成交或資料遺漏 |
| 股利／公司行動 | 已移除寫死的 0050 價格 ÷4；metadata 使用 `RAW_CLOSE_UNADJUSTED_V1`，行動與股利標 `UNSUPPORTED` | 不支援含息總報酬、一般公司行動或事件入帳 |
| 標的目錄／幣別 | 只檢查 4～6 位數字，未查商品目錄 | 數字格式不證明市場、商品類型、幣別、上市期間或可交易性 |
| 歷史快照 | 回傳 normalized hash、觀察列數／期間、解析後設定與模型版本；無原始回應／完整行情快照、擷取時間或版本資料庫 | `IDENTIFIED_NOT_ARCHIVED`；重新取得可能變動，雜湊無法重建資料 |
| 資料完整性 | 非 OK／空月份／壞列／重複或亂序日期停止計算；各資產 `coverageStatus=UNVERIFIED_CALENDAR` | 尚無日曆、生命週期或列名／標的驗證，不能標完整期間 |
| 授權 | 回傳 `LICENSING_UNVERIFIED`，`status=LIMITED_RESEARCH`、`releaseStatus=BLOCKED` | 是限制揭露，非取得許可或全面禁用網路擷取的機制 |

## 2. 來源身分、顆粒與實際查核

### 2.1 目前來源 `TWSE_STOCK_DAY_MONTHLY`

設定端點：`https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY`。參數為 `date=YYYYMM01`、`stockNo=<symbol>`、`response=json`。一份回應代表單一證券的一個月份；行情列顆粒為「來源市場、證券身分、交易日期」。證券代號必須保留字串形式，`0050` 不可轉為整數 50。

官方[個股日成交資訊頁](https://www.twse.com.tw/zh/trading/historical/stock-day.html)於 2026-10-09 查核，標示資訊自民國 99 年 1 月 4 日起提供。這是來源起始說明，不能保證每個代號自該日都有資料，也不證明停牌、下市或所有月份完整。

本次做一個有界的來源檢查：[0050／2025 年 6 月 JSON](https://www.twse.com.tw/rwd/zh/afterTrading/STOCK_DAY?date=20250601&stockNo=0050&response=json)，檢查時間為 `2026-10-08T17:01:10.868193+00:00`（台北 2026-10-09 01:01:10）。回應為 HTTP 200、`stat=OK`、`date=20250601`、16 列，欄位為日期、成交股數、成交金額、開盤價、最高價、最低價、收盤價、漲跌價差、成交筆數、註記。本次未建立原始市場資料檔、歷史快照或全期完整性證據。網站工具另一次開啟 JSON 失敗；上述結果來自本機單次 HTTP 讀取，不能宣稱來源永遠可用或後端 API 已驗證。

來源回應附註指出統計包含一般、零股、盤後定價與鉅額交易，不含拍賣及標購；因此成交股數不應直接當作某個撮合時段的可成交量。漲跌價差可能帶 `+`、`-` 或 `X` 註記；分割等事件註記不是完整公司行動事件流。[官方 STOCK_DAY 報表](https://www.twse.com.tw/exchangeReport/STOCK_DAY?date=202307&response=html&stockNo=2330)亦列示相同欄位與附註（查核 2026-10-09）。該網頁本次實際顯示 2026 年 9 月，與 URL 查詢月份不同，不將其內容當作 2023 年 7 月快照。

### 2.2 候選來源與缺口

| 需求 | 本次官方來源證據 | 仍需驗證／實作 |
|---|---|---|
| 每日全市場 OHLCV | [OpenAPI Swagger](https://openapi.twse.com.tw/v1/swagger.json)含 `/exchangeReport/STOCK_DAY_ALL`；[開放資料集 11549](https://data.gov.tw/dataset/11549)有明確的每日 CSV 資源與授權 | 不等同目前每月歷史端點；完整歷史回補能力、修訂紀錄與 API/CSV 對應需確認 |
| 市場日曆 | Swagger 含 `/holidaySchedule/holidaySchedule`；[資料集 11761](https://data.gov.tw/dataset/11761)列最新版本，通常於 12 月前提供隔年資料 | 多年歷史版、颱風等臨時休市、修正版及生效日期需另取得；年度節日列表不直接等同每日交易狀態表 |
| 標的目錄 | Swagger 含 `/opendata/t187ap03_L` 上市公司基本資料 | 不涵蓋完整 ETF／全部商品與歷史下市目錄；只取必要的商品資料，不蒐集聯絡人個資 |
| 股利 | Swagger 含 `/opendata/t187ap45_L` 上市公司股利分派情形 | 擬議／決議狀態不等同已入帳；ETF 配息、除息、發放日期、修訂與持有資格需完整事件證據 |
| 分割／停牌 | [TWSE 0050 分割新聞稿](https://www.twse.com.tw/staticFiles/news/news/tsecnews/8a8216d696b406fc0196ce27c2e90063.pdf)，公告 2025-05-14 | 單一事件查證，非通用可用事件集；完整公告時間和各事件授權需盤點 |
| 上櫃／興櫃 | 尚無 TPEx client 或本次資料集查核 | `UNKNOWN`；不把 TWSE 回應或無資料狀態當成上市櫃通用支援 |

上述 Swagger、資料集和新聞稿均於 2026-10-09 查核；它們是來源候選與欄位證據，不代表已接入程式。

## 3. 欄位來源、用途、單位與正規化

### 3.1 目前每月回應的欄位

零起算位置取自本次 `fields` 與 `data` 回應。授權狀態各欄均隨 `TWSE_STOCK_DAY_MONTHLY`，目前為 `UNKNOWN`，公開／批量使用閘門 `BLOCKED`。

| 欄位 | 來源位置 | M0 目前程式消費 | 單位／完整正規化契約（`PROPOSED`） |
|---|---|---|---|
| `symbol` | 請求 `stockNo`；回應 title 可作未來交叉查核 | 作為請求／引擎身分、manifest 與雜湊輸入；未存入 `DailyBar`，未驗證回應 title | 字串，保留前導 0；以目錄驗證歷史證券身分，避免代碼重用 |
| `market` | 此 adapter 來源身分 | 未存入 `DailyBar` | 明記 `TWSE`；不推導為 TPEx |
| `trading_date` | `data[i][0]` 日期 | 使用 | 交易所本地日期，不是 UTC timestamp；民國年 +1911，另有明確西元格式才依 schema 處理 |
| `volume` | `[1]` 成交股數 | 未使用 | 整數股數／受益權單位，非「張」；移除千分位，檢查非負整數 |
| `turnover` | `[2]` 成交金額 | 未使用 | 金額元；幣別需經商品目錄／資料定義確認；不得不分商品強制標 TWD |
| `open` | `[3]` 開盤價 | 未使用 | 每股／單位的交易幣別金額；十進位字串，保留原值 |
| `high` | `[4]` 最高價 | 未使用 | 同上；有值時檢查不低於 open/close/low |
| `low` | `[5]` 最低價 | 未使用 | 同上；有值時檢查不高於 open/close/high |
| `close` | `[6]` 收盤價 | 使用；驗證十進位／千分位文字格式，去逗號後 `new BigDecimal`，不經 double 解析 | 正有限價格；原始文字與正規化數值分開保存 |
| `change` | `[7]` 漲跌價差 | 未使用 | 符號與不比價狀態獨立保存，不能將 `X0.00` 當普通漲跌或股利 |
| `transactions` | `[8]` 成交筆數 | 未使用 | 非負整數「筆」，不等同股數 |
| `note` | `[9]` 註記 | 未使用 | 原始文字；事件指示只觸發查證，不能推算分割比例／股利日期 |
| `stat` | root `stat` | 只接受文字 `OK`；其他／缺失為 `UPSTREAM_MONTH_STATUS_UNKNOWN` | 未知原因不當作正常空月份；未來以日曆／生命週期證據分類 |
| `response_date` | root `date` | 未使用 | 回應所查月份／日期欄，不是資料發布時戳 |
| `fields/title/notes` | root 同名欄位 | 未驗證／保存 | 驗證欄位 schema、標的及統計定義；變更時停止正規化，避免位置漂移 |

M0 目前 `parseMonthResponse` 要求 object 根節點、文字 `stat=OK`、非空 array data、最多 31 列、每列為至少 7 欄的 array、日期與 close 為文字。日期格式為 2～4 位年份／1～2 位月日，民國／西元判別仍用 `year < 1911`，合法日期必須屬請求月份。價格只接受無符號十進位或合法千分位字串。[BacktestValidation.bars](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestValidation.java) 要求 close 介於 `0.0001` 與 `1000000000`（含）、去尾零後最多 8 位小數、日期唯一且嚴格遞增，總列數至多 10000。壞列不略過；非 OK、空月份（`UPSTREAM_EMPTY_MONTH`）或壞列皆停止計算，不自行分類為停牌／休市。

尚未驗證 `fields` 名稱與順序、`title` 標的、root `date` 或完整 OHLC；即使第 6 位置仍是有效數字，也不能保證來源 schema 未漂移。日曆缺列與完整上市期間仍未知。以上程式檢查是部分資料完整性防護，不是全面 schema 或市場完整性驗證。

修改前基準（歷史 finding）：`DailyBar` 為 double，client 用 `Double.parseDouble`；短列／日期分段不足被略過，非 OK 的特定字串或空白 stat 被轉成空月份，`close <= 0` 不加入結果，最終排序掩蓋來源亂序。這些描述只保留作為修正前對照，已不代表目前 client。

### 3.2 已實作的結果識別欄位（`OBSERVED_CODE`）

`BacktestService.run` 保留要求起迄日、查詢上限（未來迄日截到台北今日）與各資產實際觀察起迄日。期間不同回傳 `COVERAGE_DIFFERS_FROM_REQUEST`，未來迄日另列 `FUTURE_END_CLAMPED`；這是透明揭露，沒有完成交易日曆對帳或取得使用者的完整期間確認。

| 回傳欄位 | 目前定義／限制 |
|---|---|
| `AssetPeriod.coverageStatus/marketStatus` | `UNVERIFIED_CALENDAR`／`UNKNOWN`；`tradingDays` 只是觀察行情列數 |
| `DatasetManifest` | symbol、normalized SHA-256、列數、觀察起迄日；`calendarCoverage=UNKNOWN`、`corporateActions/dividends=UNSUPPORTED` |
| `metadata.datasetHash` | 依請求標的順序合併各 symbol 的 normalized hash；不包含原始 bytes、來源擷取時刻或日曆內容 |
| `metadata.resolvedConfig` | 保留有效標的順序、要求／有效日期、策略全部已解析參數、資金、費率、預設稅率開關及每標的實際套用估算稅率 |
| `metadata.backtestId` | 由資料雜湊、解析後設定和引擎／策略／成交／成本／調整／市場規則／指標版本生成；資料或設定改變可反映為不同識別 |
| `metadata.priceAdjustmentPolicy` | `RAW_CLOSE_UNADJUSTED_V1` |
| `metadata.reproducibilityStatus` | `IDENTIFIED_NOT_ARCHIVED`；未保存 live 原始或完整 normalized 快照，無法只靠這份回傳重建價格列 |
| `status/releaseStatus/limitations` | `LIMITED_RESEARCH`／`BLOCKED`；列出股利、行動、日曆、授權、未歸檔與假設成交等限制 |

`BacktestFingerprint.dataset` 的 canonical 內容是 UTF-8、LF 換行，首行 `normalized-close-dataset-v1`，其次 symbol，之後各列 `ISO_DATE|close\n`；close 使用 `stripTrailingZeros().toPlainString()`。這是目前 date/close 的正規化識別格式，不是第 6 節完整 source-aware 快照設計。小數表示不同但數值相同不改變價格 hash；來源版本、擷取時間或原始 bytes 改變但消費價格未變時，也不會在此 hash 反映。

### 3.3 來源治理欄位（`PROPOSED`，尚無持久化模型）

每個 normalized row 除上述可用欄位，至少關聯下列資料。不存在的 OHLCV 保持 null，禁止用 close、前一日價、0 或內插值冒充來源值。

| 欄位 | 定義與生成者 |
|---|---|
| `instrument_id/currency` | 歷史商品目錄提供，含上市／下市及代碼生效區間；未知時保持未知並限制用途 |
| `source_id/source_uri` | adapter 與實際已核准來源／參數；所有重導向目的與來源版本可追溯 |
| `source_timestamp` | 上游明確給定之發布／修訂 timestamp；沒有則 null，不以 HTTP Date 或交易日期替代 |
| `ingested_at` | 擷取回應完成時間，UTC ISO-8601；同時記 fetch_started_at，非市場交易時間 |
| `dataset_version` | 不可變快照 manifest 身分，包含價格、目錄、日曆、行動資料及正規化規則版本 |
| `raw_sha256/normalized_sha256` | 可追溯原始 bytes 和排序後的標準化內容；雜湊只證明內容身分，不提供內容重播 |
| `adjustment_status` | 原始價格／拆分調整／總報酬調整與 as-of；不以單一 boolean 隱藏不同用途 |
| `quality_status/quality_issues` | 依第 5 節產生 `PASS/RESTRICTED/BLOCKED`，含列／月份／期間的問題與證據 |
| `license_record_id` | 授權來源、適用端點、用途、條件與查核時間，連到授權矩陣 |

## 4. 原始價格、0050 分割與時間一致性

基準 `BacktestService.adjustSplit` 將 0050 的 2025-06-18 前收盤價除以 4，再交給引擎。官方 2025-05-14 新聞稿確認 4:1 分割、2025-06-11～06-17 停止買賣、2025-06-18 為新受益憑證開始買賣日。這能確認該單一事件的比例及日期，不能證明價格回溯改寫符合所有策略或帳務模型。

回溯價格調整會改變歷史名義買價和單位數；目前並無原始價與調整價雙軌、事件日持股乘 4、公告可知時間或一般行動帳務。它不是完整公司行動處理，也不是含息總報酬。

M0 目前已移除 `BacktestService.adjustSplit`，實際使用 `RAW_CLOSE_UNADJUSTED_V1`：保留來源收盤價，分割與其他公司行動標為不支援，透過 `CORPORATE_ACTIONS_UNSUPPORTED` 揭露限制，發布 gate 為 `BLOCKED`。原始價格的分割跳動仍可能造成報酬、回撤與訊號失真；不能把移除特殊修正描述為已解決公司行動，也不能宣稱跨事件績效完整。尚無通用事件偵測／逐事件阻擋，未支援事件的判讀仍需額外證據。

未來若實作公司行動，需分開保存 raw price、明確調整價序列、事件（公告可知時間／生效日／持股變化／現金流），固定 adjustment_version 和 as_of。交易使用的價格、指標使用的序列必須在引擎契約寫清楚；禁止同時用含息調整價格和股利現金流重複計算。只知道分割比例不能授權下載或重製公告全文。

## 5. 完整資料品質閘門（`PROPOSED`，部分程式防護已實作）

閘門先驗證可用資料，再決定可研究的能力。不能因資料源回 HTTP 200 或已成功編譯而標為資料通過。目前 DQ-02 已有非 OK／空月份／列形狀／日期月份檢查，DQ-03 已有 date/close 值與序列檢查；未建立欄名／title 驗證、隔離資料持久化、日曆／行動／授權與完整可重播管線。第 3.2 節的識別 metadata 只涵蓋 DQ-07 的部分需求。

| 閘門 | 必須保存的證據 | 失敗／未知時處理 |
|---|---|---|
| DQ-01 授權與來源 | 精確端點／資料資源與用途授權紀錄、版本日期 | 適用範圍 UNKNOWN → 儲存、批量擷取與公開結果 `BLOCKED` |
| DQ-02 傳輸與 schema | 每月 status、JSON/schema/fields、標的與查詢月份檢查 | 非預期欄位、無 stat、錯標的、來源錯誤 → `BLOCKED`；禁止變成成功空月份 |
| DQ-03 列值完整性 | 非空交易日期、正有限 close、唯一日期、嚴格升序；OHLC/V 有值才做一致性檢查 | 異常列不得靜默遺失；列保留於隔離紀錄，整體 `BLOCKED` |
| DQ-04 期間與日曆 | requested/effective/available 範圍，逐月擷取清單，歷史日曆與標的上市／停牌狀態 | 完整性未證實 → `RESTRICTED`；所需日期資料遺漏 → `BLOCKED`；不可偷偷縮短 |
| DQ-05 公司行動相容 | action coverage、公告與生效時間、adjustment_version | 跨未支援事件 → 不完整價格研究，阻擋總報酬／一般交易帳務宣稱 |
| DQ-06 策略需求 | execution_model 所需欄位清單 | 僅 close → 阻擋 next-open、intraday high/low、成交量限制模型 |
| DQ-07 可重現 | immutable dataset manifest + 可讀快照 + engine/config version | 只有 hash 或重新向上游查詢 → 可識別但非可重播；公開可信度 `BLOCKED` |

缺行情必須能分類為 `MARKET_CLOSED`、`INSTRUMENT_SUSPENDED`、`NO_TRADE`、`NOT_LISTED`、`DELISTED`、`SOURCE_MISSING` 或 `UNKNOWN`。價格列本身不能區分這些狀態；沒有外部證據時一律 `UNKNOWN`。不可憑週末／假日推定所有缺列合理，也不可將已停止買賣的 0050 分割期間填成可成交價格。

## 6. 可重現快照與擷取稽核設計（`PROPOSED`）

在授權允許保存的來源上，建立不可變快照；本 M0 文件並未建立這套儲存管線。對目前授權未明的歷史端點，不為完成規格而保存市場原始資料。

1. 擷取紀錄固定 source_id、完整 request_uri、symbol/month、fetch_started_at、ingested_at、HTTP status、content-type、redirect target、上游明確 timestamp、schema fingerprint。不要保存 Cookie、Authorization 或帳密。
2. raw 層保存收到的 bytes 與 SHA-256；各 symbol/month 成功、空資料、來源錯誤均有 manifest 條目。空月份仍須狀態證據，不以缺檔表示正常。
3. normalized 層以 `(market, instrument_id, trading_date)` 排序，固定 UTF-8、欄序、ISO 日期、十進位字串與 null 表示；保留 row→raw 回應／列位置的 lineage。固定 normalizer_version、price_policy、currency/status；修改規則生成新版本。
4. dataset manifest 包含每份 raw/normalized hash、列數、期間、品質報告、授權紀錄、目錄／日曆／行動快照 hash 及版本。修訂資料不得覆寫舊版本；訂正原因與 prior_version 可追溯。
5. 回測 run 關聯 dataset_version、engine_version、全部解析後設定（含預設值、費率和成交模型），並可在不連網條件下以保留快照重跑。逐次擷取時間屬 metadata；same-data replay 不應因 run timestamp 改變資料身分。

synthetic fixture 與 live dataset 分開。M0 會計測試使用合成價格／日期和獨立預期數值，manifest 明記 `source_type=SYNTHETIC`；不能以行情源的當日成功回應當作 golden accounting fixture。市場快照保留與刪除期限依精確授權和後續產品決策制定，當前 `UNKNOWN`，不虛構已核准保留年限。

## 7. M0 最終程式核對與驗收

已於 2026-10-09 重新讀取第 1 節所列目前程式檔，核對文件與程式欄位／常數／分支。這是資料盤點的文件整合核對；本 worker 未執行後端會計測試、編譯、HTTP API、瀏覽器或完整市場期間驗證，也不宣稱其他 worker 尚在進行的測試通過。

| 驗收項目 | 所需證據 | 狀態 |
|---|---|---|
| 每一個消費／來源欄位有用途、單位和授權狀態 | 第 3 節與授權矩陣 | 文件已盤點；數值／幣別未確定者保留 UNKNOWN |
| 精確 monthly／ALL 端點分清楚 | 官方 JSON／Swagger／資料集 11549 的資源 URL | 已查核；monthly 的權利範圍仍 UNKNOWN |
| 原始 close 契約和缺口揭露與目前程式一致 | 第 1 節 source links、decimal parser、service、response、fingerprint | 已完成程式閱讀核對：BigDecimal／RAW_CLOSE_UNADJUSTED_V1，移除 0050 寫死調整；不等同 runtime 通過 |
| 有界 live check 與 synthetic/replay 分開 | 第 2 節與第 6 節 | 已分開；只查一個月份，未完成 live 全期／後端 API 驗證 |
| 非 OK／空月份／錯誤列不靜默成功 | `parseMonthResponse`／`BacktestValidation.bars` | 已有停止計算分支與錯誤碼；未在本 worker 執行來源 stub／測試 |
| 資料識別與能力限制 | `BacktestFingerprint`、`ResolvedConfig`、`DatasetManifest` | 已核對 normalized hash／解析設定／版本欄位；`UNVERIFIED_CALENDAR`、`IDENTIFIED_NOT_ARCHIVED`，live 快照／日曆／事件仍未實作 |
| 完整欄位／標的／日曆 schema 驗證 | DQ-02／DQ-04 的完整證據 | 尚未實作；未驗證 fields/title/root date，不宣稱可識別全部資料缺漏 |
| 公開發布 gate | 授權、完整性、行動及可重播證據 | `BLOCKED` |

M0-DATA 文件盤點與目前程式整合核對已完成；全部 UNKNOWN 均保留待取得證據與決策角色，產品 gate 維持 `BLOCKED`。下一步由資料／產品負責人依授權矩陣取得歷史端點或替代資料商品的精確授權、決定需支援的交易模型和事件範圍；只有之後的有界實作任務才可建立持久化管線。
