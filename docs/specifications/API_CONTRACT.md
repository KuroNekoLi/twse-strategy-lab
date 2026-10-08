# M0 回測 API 契約

端點維持 `POST /api/v1/backtests`，JSON。保留既有請求欄位與主要結果欄位；M0 增加可追溯資料、帳務紀錄、限制與錯誤代碼。Java 17 / Spring Boot 4.1.1。

## 請求

```json
{
  "symbol": "2330",
  "symbols": ["2330"],
  "from": "2024-01-01",
  "to": "2024-01-31",
  "strategy": "ma-crossover",
  "fastWindow": 2,
  "slowWindow": 3,
  "rsiWindow": 14,
  "rsiBuyThreshold": 30,
  "rsiSellThreshold": 55,
  "bollingerWindow": 20,
  "bollingerMultiplier": 2,
  "breakoutWindow": 20,
  "drawdownBuyPercent": 20,
  "profitSellPercent": 20,
  "initialCapital": 1000,
  "monthlyContribution": 0,
  "commissionRate": 0.001425,
  "sellTaxRate": 0.003,
  "useMarketTaxDefaults": false
}
```

此 JSON 只是請求範例；不代表 live 行情已可用或測試數據來源。

| 欄位 | 限制與預設 |
|---|---|
| symbol | 必填，4 至 6 位數字，trim；symbols 有值時仍需提供合法 symbol |
| symbols | 可選；空則使用 symbol，原始清單最多三筆，各筆不可 null；trim 並保留首次出現順序去重；有值時結果主標的使用清單第一筆 |
| from/to | ISO 日期，起日不早於 2010-01-01，from ≤ to，from 不可超過台北今日；查詢上限至多 204 個月 |
| strategy | 可選，空或 null 預設 ma-crossover；只接受五個已定義 key |
| fastWindow/slowWindow | 必填，2..500；MA 策略 fast < slow |
| rsiWindow | 2..100，預設 14 |
| rsiBuyThreshold/rsiSellThreshold | 有限數，1..49 / 51..99；預設 30 / 55 |
| bollingerWindow/multiplier | 2..200 / 有限數 0.5..5；預設 20 / 2 |
| breakoutWindow | 2..250；預設 20 |
| drawdownBuyPercent/profitSellPercent | 有限數 1..80 / 1..200；預設 20 / 20 |
| initialCapital | 必填，0.01..1,000,000,000,000，最多兩位小數（去除尾零後） |
| monthlyContribution | 必填，0..1,000,000,000,000，最多兩位小數 |
| commissionRate/sellTaxRate | 必填，比例而非百分比：0（含）至 1（不含），最多八位小數；買賣手續費 + 採用賣出稅率 < 1 |
| useMarketTaxDefaults | null 預設 true；true 時以代碼前綴估算每標的稅率，於 metadata 列出 |

例如 UI 的 0.1425% 需送 `0.001425`；不要送浮點運算殘值 `0.0014249999999999998`。現有成本預設可以使用上列精確八位小數內表示。可選參數仍驗證範圍，即使本次策略未使用該參數。超過今日的 to 不隱藏：保留 requestedTo，在 resolvedConfig.effectiveTo 顯示查詢上限並加入 FUTURE_END_CLAMPED。

## 回應

HTTP 200 是有限研究計算成功，**不表示完整行情、實際成交或通過發布閘門**。頂層 `status=LIMITED_RESEARCH`、`releaseStatus=BLOCKED`。

| 欄位 | 意義 |
|---|---|
| symbol/symbols | 已解析標的及順序 |
| from/to/tradingDays | 主標的實際觀察的資料起迄与筆數，保留舊欄位 |
| requestedFrom/requestedTo | 原始要求期間 |
| assets | 每標的 symbol/from/to/tradingDays/requestedFrom/requestedTo/coverageStatus/marketStatus；目前 UNVERIFIED_CALENDAR / UNKNOWN |
| dataSource/assumptions | 來源名稱與可讀假設 |
| limitations | `{code,message}`；股利、公司行動、日曆、授權、未封存快照、成本簡化等限制；邊界有差异亦列出 |
| metadata | 所有版本、已解決設定、資料指紋及重現狀態 |
| results | 每標的策略与 DCA，各自獨立帳戶，不是合併投資組合 |

metadata 字段：`backtestId`、`engineVersion`、`strategyVersion`、`executionModel`、`executionModelVersion`、`costModelVersion`、`priceAdjustmentPolicy`、`marketRulesVersion`、`metricsVersion`、`datasetHash`、`reproducibilityStatus`、`resolvedConfig`、`datasets`。目前模型 NEXT_CLOSE_PROXY、政策 RAW_CLOSE_UNADJUSTED_V1、重現狀態 IDENTIFIED_NOT_ARCHIVED。

resolvedConfig 保留已解決 symbols/from/to/effectiveTo/strategy、所有策略參數、initialCapital/monthlyContribution/commissionRate/sellTaxRate/useMarketTaxDefaults 及 appliedSellTaxRates（symbol → 實際採用稅率）。datasets 各列 symbol/sha256/rows/from/to/calendarCoverage/corporateActions/dividends，目前日曆 UNKNOWN、公司行動與股利 UNSUPPORTED。不增加 raw bars 快照公開匯出。

results 的既有字段：key/name/endingValue/contributed/totalReturn/annualizedReturn/maxDrawdown/series。**totalReturn 保留投入損益比例；annualizedReturn 改為已定義 TWR 年化，maxDrawdown 改為現金流中性正規化 NAV 回撤**，客戶端必須依 annualizationBasis 決定顯示口徑，不能把新舊結果混同。

新增：profit/timeWeightedReturn/annualizationBasis/trades/dailyEquity/metricWarnings。報酬、回撤、dailyReturn 均為百分比（10 表示 10%）。annualizedReturn/timeWeightedReturn 可為 null；不可計算原因列於 metricWarnings，禁止呈現虛構 0。

所有金額為十進位 JSON number（不是 string），帳務採 BigDecimal 二位 HALF_UP；price 保留來源十進位精度，averageCostAfter 八位 HALF_UP；股數為 long 整數。JSON 消費端如需對帳，須使用十進位套件或以十進位文本解析，JS number 僅適合顯示。

- series：完整日資料的 `{date: YYYY-MM-DD,value: equity}`；本版不按 320 筆下採樣，不用月份代替日期。
- trades：signalDate（DCA 預定時程為 null）、executionDate、side（BUY/SELL）、status（FILLED / SKIPPED_INSUFFICIENT_CASH）、reason、quantity、price、gross、fee、tax、cashAfter、positionAfter、averageCostAfter。不足現金的意圖 quantity=0。末日未來訊號無下一筆價格時不捏造成交。
- dailyEquity：date/equity/cash/shares/contributed/externalFlow/positionCost/normalizedNav/dailyReturn/drawdown；dailyReturn 分母為零時 null。

## 錯誤

保留既有 `error` 字串，增加穩定 `code`：

```json
{"code":"DATA_INTEGRITY_FAILED","error":"2330／2024-01：行情日期重複或未依時間遞增，已停止計算。"}
```

| HTTP | code | 觸發 |
|---|---|---|
| 400 | INVALID_INPUT | null/格式錯誤、JSON 無法解析、非法參數、成本 ≥100%、超上限 |
| 404 | NO_MARKET_DATA | 有效載入資料篩選要求期間後無資料；狀態原因 UNKNOWN |
| 422 | INSUFFICIENT_DATA | 無法暖機與至少一次下一筆執行 |
| 422 | DATA_INTEGRITY_FAILED | 空/null、非正價格、超價格精度／範圍、重複、未排序、錯誤列／月份 |
| 502 | UPSTREAM_MONTH_STATUS_UNKNOWN | 月份 stat 不是 OK 或缺少；不推斷尚未上市、停牌或假日 |
| 502 | UPSTREAM_EMPTY_MONTH | OK 但月份 data 空或不是陣列 |
| 502 | UPSTREAM_DATA_UNAVAILABLE | 網路、非成功 HTTP、無法解析上游 JSON |
| 502 | UPSTREAM_FAILURE | 其餘非預期失敗；公開訊息不透出上游 payload 或内部錯誤詳細內容 |

只要某一標的載入失敗，整個請求失敗；不回傳看似完整的部分成功。月份資料嚴格檢查：不排序修正、不去重、不跳過錯誤列。收盤價範圍 0.0001..1,000,000,000、最多八位小數，合成／直接輸入最多 10,000 筆；無限大、NaN 或缺價皆不可作為收盤。

## 驗證與相容範圍

合成來源的真實本地 HTTP 測試驗證 Spring 啟動、舊請求成功、數值帳務、研究限制、ISO 日日期，以及 null 金額／壞 JSON 的 400 代碼。單元測試驗證各策略、SHA-256 與錯誤路徑。這些不等於 live TWSE 可用性、瀏覽器或正式市場回測驗收。詳見 `../quality/GOLDEN_TEST_FIXTURES.md`。
