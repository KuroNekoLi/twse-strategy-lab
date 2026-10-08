# M0 資料授權矩陣

文件版本：`M0-LICENSE-2`。查核日期：2026-10-09（Asia/Taipei）。執行者：`/root/m0_data`，Data Engineer / Technical Writer。範圍：EPIC-01；依 `產品規劃.md` 第 8、13、21 節建立來源、使用目的、權利狀態與待補證據。

這是官方條款與專案證據盤點，不是法律意見，也不是新增資料購買／發布的核准。未知項目保留 `UNKNOWN`，必要使用權未明的產品閘門為 `BLOCKED`。本次未簽約、付費、訂購、傳送外部詢問、建立市場資料庫或發布資料。

## 1. 狀態與授權判讀

| 狀態 | 意義 |
|---|---|
| `PUBLIC_REACHABLE` | 網頁／規格／一次回應可讀；只證明存取觀察，不授與其他用途權利 |
| `YES_CONDITIONAL` | 精確資源有發布的授權條款支持該用途，仍須滿足顯名等條件與來源／用途適用性；不是專案已完成控制 |
| `UNKNOWN` | 缺少精確端點、資源、期間或用途適用證據；不得推定允許 |
| `NOT_ACQUIRED` | 有商品／契約途徑，但本專案未購買、簽約或取得權利 |
| `BLOCKED` | 必要證據未滿足時的行動／發布 gate，不等同法律上已判定全面禁止 |

免費、API 200、CSV 下載按鈕、已標來源或「研究用途」文字，都不能單獨證明儲存、商用、再散布、衍生回測報告、原始／調整資料下載的權利。僅存在明確開放授權的精確資源時，按該條款判讀；不能把開放資料例外延伸到所有 TWSE 網頁和歷史端點。

## 2. 官方證據登錄（均於 2026-10-09 查核）

| ID | 官方來源與版本／日期 | 本次核對結果與限度 |
|---|---|---|
| L-01 | [TWSE 使用條款](https://www.twse.com.tw/zh/terms/use.html)，頁面未顯示明確版本／修訂日期 | 第 6 項限制非同意方式之自動化下載；第 8 項保留智慧財產使用權，例外是 TWSE 已授權政府資料開放平臺公眾使用的資料。顯名不會自動解除前述權利條件 |
| L-02 | [交易資訊使用管理辦法、契約、收費標準入口](https://www.twse.com.tw/zh/products/information/use.html)，頁面未顯示修訂日期 | 入口說明申請使用應遵守辦法、訂適當契約及繳費；不能據此直接認定現行 monthly 查詢端點需哪份契約／多少費用 |
| L-03 | [交易資訊使用管理辦法 PDF](https://www.twse.com.tw/downloads/zh/products/regulation_use.pdf)，文件列示最後修訂公告 2021-12-24（民國 110 年） | 第 3 條含交易及衍生資訊定義；第 28 條費用及權利金、第 29 條盤後資訊需經同意才可能不收費。一般管理規定不替代精確開放資料許可；最新適用性仍須按用途確認 |
| L-04 | [收費標準 PDF](https://www.twse.com.tw/downloads/zh/products/table_fee.pdf)，列 2026-07-08 修訂非揭示用途授權費 | 即時、延遲等有個別費用與授權範圍；不能把即時報價或非揭示費率當 monthly 歷史資料的報價 |
| L-05 | [非揭示用途終端用戶指引](https://www.twse.com.tw/downloads/zh/products/non_display_user.pdf)，`2026 版2` | 指引定義針對資訊源形式取得即時資訊之非揭示用途，不能直接套用盤後歷史 close 或反向推定其免費 |
| L-06 | [政府資料開放授權條款第 1 版](https://data.gov.tw/license)，2015-07-27 訂定 | 第 2 項列不限目的等利用與衍生物，第 3 項要求資料及衍生物的顯名；條款適用於依其釋出的開放資料，非所有公開網頁。來源仍有免責及停止供應條款 |
| L-07 | [個股日成交開放資料集 11549](https://data.gov.tw/dataset/11549)，詮釋資料更新 2025-05-01 11:23 | 每日、免費、OGDL 第 1 版；精確資源 URL 為 `https://www.twse.com.tw/exchangeReport/STOCK_DAY_ALL?response=open_data`，備註連官方 Swagger。此資源是 ALL CSV，未標示 monthly 歷史端點 |
| L-08 | [OpenAPI Swagger JSON](https://openapi.twse.com.tw/v1/swagger.json)，info version `1.0`，無可見發布日期 | info 連使用條款及 OGDL；含 `/exchangeReport/STOCK_DAY_ALL`，不含目前 `/rwd/zh/afterTrading/STOCK_DAY`。API 有列名，並不能證明兩個端點相同範圍／歷史覆蓋 |
| L-09 | [盤後資訊與歷史交易資料](https://www.twse.com.tw/zh/products/information/history.html)，未顯示修訂日期 | 官方提供資訊商店途徑；本專案尚未取得商品契約或付費權利 |
| L-10 | [每日收盤行情商品](https://eshop.twse.com.tw/zh/product/detail/cfec9a1470e448ec91bfde006db361e8)，查核頁面列價格及各資料格式生效版 | 顯示內部 NT$1,000/月、外部 NT$1,500/月；內部用途不得公開／移作他用，外部用途亦有線上訂購條款。價格只適用此商品、查核時點；本專案實際報價／費用 UNKNOWN |
| L-11 | [開休市資料集 11761](https://data.gov.tw/dataset/11761)，詮釋資料更新 2026-08-21 15:31 | 免費、OGDL 第 1 版；提供最新版本，通常 12 月前提供隔年。未取得所有歷史版及臨時修訂的適用證據 |
| L-12 | [0050 分割新聞稿](https://www.twse.com.tw/staticFiles/news/news/tsecnews/8a8216d696b406fc0196ce27c2e90063.pdf)，2025-05-14 | 可查證 4:1、停牌與恢復買賣日期；未因此建立通用行動資料授權或全文再散布權 |

本次成功讀取官方網頁與 PDF 文字、Swagger。政府資料平臺連結之 CSV 在網頁工具因 CSV content-type 無法解析，已取得資源 URL，但未下載或驗證其實際完整內容。這不妨礙查核資料集授權記載，也不構成 API/CSV 資料一致性的證明。法規分享知識庫網頁曾無法開啟；L-03/L-04 內容取自官方入口連結的 PDF。

## 3. 資源 × 用途矩陣

`YES*` 代表 `YES_CONDITIONAL`，僅對精確資源、符合 L-06 與其資料集條件的利用。`UNKNOWN` 不等於禁止，也不等於允許。所有產品權利與功能支援均須分開判定。

| 資源／專案狀態 | 公開可讀 | 商用 | 後端保存／快照 | 原始／轉換再散布 | 衍生回測報告 | 使用者下載 | gate／需補證據 |
|---|---|---|---|---|---|---|---|
| R-01 monthly `STOCK_DAY`：目前 client 消費日期、close，其他回應欄位未消費 | 一個月份有 `PUBLIC_REACHABLE` 證據 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | `BLOCKED`；需證明精確歷史端點、日期範圍及自動化存取方式受何授權／契約涵蓋；L-01/L-02/L-07 未完成映射 |
| R-02 dataset 11549 的 `STOCK_DAY_ALL?response=open_data` CSV：候選，未接入 | 官方資料集列資源 | YES* | YES* | YES* | YES* | YES* | 已有資源級 OGDL 記載；接入前保存顯名／版本、確認實際內容、幣別／來源時間及歷史覆蓋。不清除 R-01 gate |
| R-03 OpenAPI `/exchangeReport/STOCK_DAY_ALL`：候選，未接入 | 官方 Swagger 可讀 | UNKNOWN（API→R-02 精確映射待核） | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | L-08+L-07 是相同報表名稱與欄位的證據；取得明確 API 資源適用關係後才能沿用 R-02 條件，不能只靠同名 |
| R-04 日曆 dataset 11761：候選，未接入 | 官方資料集可讀 | YES* | YES* | YES* | YES* | YES* | 僅該資源及版本；歷史版／臨時公告的授權與完整性 UNKNOWN |
| R-05 公司目錄／股利 OpenAPI：未接入 | Swagger 列介面 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | 逐一找精確資料集、授權、ETF 與歷史範圍；不能憑 OpenAPI 總體說明一律套用 |
| R-06 分割／除權息／停牌等公告事件集：無管線 | 單一 0050 官方公告可讀 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | 需通用事件資源與公告可知時間、欄位授權；事實核對不等於全文重製授權 |
| R-07 Data E-Shop 每日收盤行情：候選、`NOT_ACQUIRED` | 商品說明可讀 | UNKNOWN | NOT_ACQUIRED | NOT_ACQUIRED | UNKNOWN | NOT_ACQUIRED | 需選內部／外部用途、取得最新訂購條款／契約與使用者範圍確認；內部商品明確不可對外公開 |
| R-08 TPEx／其他供應商：未查核／未接入 | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | 另有來源與契約，不在本次 TWSE 查核範圍；M0 不宣稱上市櫃通用覆蓋 |
| R-09 M0 synthetic fixtures：由專案創作的價格／日期 | 本地測試資產 | 不需市場資料供應授權 | 合成資料可納入版本控制 | 非市場原始資料 | 僅測試／示例，不當市場研究證據 | 不在本次產品實作範圍 | 與市場資料及其授權分開；保留 synthetic 標記與來源說明 |

R-01 也沒有已核准的呼叫速率或批次抓取證據。目前 [TwseMarketDataClient](../../backend/src/main/java/uk/kuronekoli/strategylab/market/TwseMarketDataClient.java) 仍有三個月份並行、批次間約 120ms，以及特定轉址異常重試；這是實作觀察，不是符合來源流量政策的證據。若授權需限定速率，必須由來源條款／書面答覆取得，不在文件自行宣稱合法安全值。

## 4. 待取得證據與決策責任

| 問題／用途 | 下一份必要證據 | 負責角色／接受條件 |
|---|---|---|
| 現行歷史端點能否自動呼叫、保存與研究計算 | 官方資料集記錄／指定介面說明，或 TWSE／授權供應商對 `/rwd/zh/afterTrading/STOCK_DAY`、2010 起歷史範圍及使用方式的書面許可／契約 | 資料／產品負責人取得；必須逐項覆蓋自動化、保存、商用和下游客戶，不以 general OpenAPI 連結代替 |
| 回測 summary、資產曲線、交易明細、分享、下載可否提供給使用者 | 精確衍生報告與重建原始資料能力的用途說明／授權範圍；包含免費／付費、public/private、使用者類型與地區 | 產品負責人界定實際功能；資料／法律專業判讀條款，必要時取得權利人回答。尚未發出任何詢問 |
| 可留存多久、可否備份、授權終止後處理 | 保留期限、內部複製／備份、刪除／舊結果使用條件；確認可能例外 | 資料／維運負責人建立 manifest 及刪除策略；當前期限 UNKNOWN，不先保存不明市場原檔 |
| 每日 CSV 是否足以回補 2010 起歷史 | 官方介面歷史覆蓋、指定歷史資源與其獨立許可；資料完整性樣本 | 資料負責人；僅目前每日資源不能證明 17 年歷史權利和完整性 |
| ALL API 與有明確許可的 CSV 關係 | 資料提供機關明確資源映射或官方資源記錄指向該 API；保存查核版本 | 資料負責人；欄位相似只支持技術候選，未補完許可適用性 |
| 商品費用與例外 | 精確商品報價、最新線上訂購條款、內外部用途及使用者資格；是否仍需額外申請／簽約 | 產品負責人決定購用；任何付費或簽約需使用者明確授權。費用不從即時標準估算 |
| 來源標示 | 精確資源的來源單位、年份、名稱／版本和許可證文字，轉換說明 | 資料／技術寫作者；資料與衍生物均附完整顯名，不能只寫泛稱 TWSE |

## 5. M0 接受與公開 gate

M0 盤點接受條件：每個目前消費欄位能連到 R-01，候選來源不冒充現行來源，每個用途有狀態、來源日期和下一份必要證據。UNKNOWN 可作為盤點結果，但不能當作使用許可。所有儲存與可重現管線提案見 [MARKET_DATA_SPEC.md](MARKET_DATA_SPEC.md)，它們尚不因寫入文件而實作或獲准。

M0-DATA 授權盤點已完成；公開 gate 維持 `BLOCKED`：目前 monthly 歷史端點授權適用性、歷史完整性、公司行動／股利與可重播資料仍不足。已重新核對 [BacktestService](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestService.java) 的 `LICENSING_UNVERIFIED`／`releaseStatus=BLOCKED`、[BacktestFingerprint](../../backend/src/main/java/uk/kuronekoli/strategylab/backtest/BacktestFingerprint.java) 的 `IDENTIFIED_NOT_ARCHIVED` 與 [BacktestResponse](../../backend/src/main/java/uk/kuronekoli/strategylab/api/BacktestResponse.java) 的解析後設定／dataset hash 欄位。這些揭露和識別控制已存在於程式，仍未取得授權、建立市場快照或對儲存／散布作出新的許可。

本次最終整合核對只閱讀程式與文件，未新增 live 呼叫或市場原檔，也未執行會計測試、瀏覽器、後端 runtime 或法律審查。原始／normalized 快照及完整擷取時間規格仍為提案；已存在的雜湊不是已歸檔資料的證據。

若後續選用 R-02/R-04 的精確開放資源，應以已確認條款執行顯名與版本保存；此處不增設其條款沒有要求的再核准流程，也不將同一許可跨來源套用。M0 不包含發布、對外資料下載、API 再散布、資訊商品購買或授權洽談；後續行動仍受使用者授權範圍約束。

## M1 標的目錄精確來源查核（2026-10-09）

| 目錄內容 | 精確資料資源 | 公開詮釋資料 | 使用範圍與限制 |
|---|---|---|---|
| 上市公司 | TWSE OpenAPI `/opendata/t187ap03_L`（Swagger 名稱：上市公司基本資料）；[data.gov.tw dataset/18419](https://data.gov.tw/dataset/18419) | OGDL v1.0、免費、月更新；欄位含公司代號、公司簡稱／名稱；詮釋資料更新時間 2024-11-25 | 僅映射代號、名稱、出表日期及市場種類。來源另有公司及個人聯絡資料，禁止在本產品 API／UI暴露不必要欄位 |
| 上市基金／ETF | TWSE OpenAPI `/opendata/t187ap47_L`（Swagger 名稱：基金基本資料彙總表）；[data.gov.tw dataset/157399](https://data.gov.tw/dataset/157399) | 資料集說明為 ETF 基本資料；OGDL v1.0、免費、月更新；欄位含基金代號、基金簡稱／中文名稱；詮釋資料更新時間 2025-01-03 | 僅映射代號、名稱、出表日期及基金種類；目錄可搜尋不代表歷史行情支援、行情完整或可發布 |

上列資料集記錄均引用 [TWSE 官方 OpenAPI 說明](https://openapi.twse.com.tw/)；OpenAPI 中的 endpoint 名稱與資料集名稱相符。2026-10-09 僅作一次唯讀 GET schema/摘要抽樣（不保存完整回應）：公司資源 HTTP 200、1,095 筆，包含 `公司代號`、`公司名稱`、`出表日期`；基金資源 HTTP 200、271 筆，包含 `基金代號`、`基金簡稱`、`出表日期`。抽樣只佐證介接欄位與當時行數，不是目錄長期完整性、API 可用性或更新 SLA 的保證。

產品須保留逐筆 `出表日期`、提供者、資料集識別碼及來源連結，並附政府資料開放授權顯名。只讀取必要欄位、即時搜尋呈現；不保存完整來源檔、不提供企業負責人／地址／電話／電子郵件等聯絡資料。目錄的 OGDL 授權不能延伸至月歷史行情端點 `/rwd/zh/afterTrading/STOCK_DAY`、歷史資料保存或衍生回測結果；那些用途仍依 R-01 維持 `UNKNOWN`／發布 `BLOCKED`。
