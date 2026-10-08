# Angular 與 Spring Boot API 契約

Angular 前端使用 `POST /api/v1/backtests` 呼叫 Spring Boot。Pages 僅託管靜態前端；API 需要獨立的 Java 主機與 HTTPS 網址。

## 請求

```json
{
  "symbol": "0050",
  "symbols": ["0050", "2330"],
  "strategy": "drawdown-entry",
  "from": "2010-01-01",
  "to": "2026-12-31",
  "fastWindow": 20,
  "slowWindow": 60,
  "rsiWindow": 14,
  "rsiBuyThreshold": 30,
  "rsiSellThreshold": 55,
  "bollingerWindow": 20,
  "bollingerMultiplier": 2,
  "breakoutWindow": 20,
  "drawdownBuyPercent": 20,
  "profitSellPercent": 20,
  "monthlyContribution": 10000,
  "initialCapital": 100000,
  "commissionRate": 0.001425,
  "sellTaxRate": 0.001,
  "useMarketTaxDefaults": true
}
```

`symbols` 可省略，最多 3 檔、每檔為 4 至 6 位數字。策略為 `ma-crossover`、`rsi-reversion`、`bollinger-reversion`、`breakout`、`drawdown-entry`。百分比門檻使用百分點；手續費與交易稅使用小數。

## 回應

```json
{
  "symbol": "0050",
  "symbols": ["0050"],
  "from": "2010-01-04",
  "to": "2026-09-30",
  "tradingDays": 4200,
  "assets": [{"symbol":"0050","from":"2010-01-04","to":"2026-09-30","tradingDays":4200}],
  "dataSource": "臺灣證券交易所 STOCK_DAY",
  "assumptions": ["日收盤價", "不含配息", "月初投入"],
  "results": [{
    "key": "0050-drawdown-entry",
    "name": "0050 · 回跌買進 / 獲利賣出 · 回跌 20% 買 / 獲利 20% 賣",
    "endingValue": 0,
    "contributed": 0,
    "totalReturn": 0,
    "annualizedReturn": 0,
    "maxDrawdown": 0,
    "series": [{"date":"2010-01","value":0}]
  }]
}
```

每個標的回傳所選策略和定期定額兩組結果。錯誤回應格式為 `{ "error": "說明" }`。CORS 來源由 `CORS_ALLOWED_ORIGIN_PATTERNS` 設定。

M0 保留以上欄位，追加可追溯的版本、資料識別、請求期間、限制、成交與每日帳務。錯誤保留 `error` 並追加 `code`。精確欄位與計算定義以 [API_CONTRACT](specifications/API_CONTRACT.md) 與 [BACKTEST_ENGINE_SPEC](specifications/BACKTEST_ENGINE_SPEC.md) 為準；此處的舊版回應例只呈現相容欄位，不代表完整 M0 回應。

## 計算與資料處理

- `BacktestController` 接收 JSON，`BacktestService` 驗證期間、策略與標的並協調資料和計算。
- `TwseMarketDataClient` 呼叫 TWSE `STOCK_DAY`，處理民國日期和 TWSE 307 轉址；每次最多三個月份並行，僅允許 HTTPS 的 `twse.com.tw` 網域轉址。無效資料與空月份不再靜默略過；仍缺乏可證明逐日完整性的交易日曆與停牌資料。
- `BacktestEngine` 使用前一筆已完成觀察資料產生訊號，以下一筆觀察資料收盤作 `NEXT_CLOSE_PROXY` 假設性計價。計算五種策略並提供每月定期定額基準；訊號、整數股數、費用、剩餘現金與每日資產均可追蹤。此模式不保證真實委託可在該價格成交。
- 使用原始未調整收盤價；移除原本對 0050 的固定除以 4 特例。未處理股利及公司行動，不能宣稱完整含息總報酬。滑價、最低手續費、交易日曆與特殊商品規則亦未支援，發布關卡為 `BLOCKED`。
- `totalReturn` 是損益／總投入比率；`timeWeightedReturn` 是排除外部投入直接影響的每日 TWR；年化與最大回撤依 TWR 口徑。金額採十進位與明確捨入，詳細公式見引擎規格。
- 活行情 SHA256 識別本次消費的資料，未封存原始資料時僅為 `IDENTIFIED_NOT_ARCHIVED`，不能保證日後重取同樣資料。固定合成行情測試的重播證據另列於 [GOLDEN_TEST_FIXTURES](quality/GOLDEN_TEST_FIXTURES.md)。

## 後端部署環境變數

- `PORT`：主機提供的 HTTP port，預設 8080。
- `CORS_ALLOWED_ORIGIN_PATTERNS`：逗號分隔的前端來源，例如 `https://kuronekoli.github.io,https://finance.kuronekoli.uk,http://localhost:4200`。
- Zeabur 服務根目錄設為 `backend`，Dockerfile 會建置並啟動 Spring Boot；健康檢查端點為 `GET /api/health`。

Pages workflow 使用 Actions Variable `BACKEND_API_URL` 產生前端執行期設定 `frontend/public/config.js`。設定值是 API 根網址，不能以 `/api/v1/backtests` 結尾。
