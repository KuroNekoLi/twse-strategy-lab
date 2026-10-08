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

## 計算與資料處理

- `BacktestController` 接收 JSON，`BacktestService` 驗證期間、策略與標的並協調資料和計算。
- `TwseMarketDataClient` 呼叫 TWSE `STOCK_DAY`，處理民國日期和 TWSE 307 轉址；每次最多三個月份並行，僅允許 HTTPS 的 `twse.com.tw` 網域轉址。
- `BacktestEngine` 使用昨日已完成行情產生訊號，並以當日收盤價模擬成交。計算雙均線、RSI、布林通道、突破及回跌／報酬門檻策略，也提供每月定期定額基準。
- 0050 於 2025-06-18 進行 1 拆 4；已將此日前的日收盤價除以 4。結果不含配息、滑價和券商最低手續費。

## 後端部署環境變數

- `PORT`：主機提供的 HTTP port，預設 8080。
- `CORS_ALLOWED_ORIGIN_PATTERNS`：逗號分隔的前端來源，例如 `https://kuronekoli.github.io,https://finance.kuronekoli.uk,http://localhost:4200`。
- Zeabur 服務根目錄設為 `backend`，Dockerfile 會建置並啟動 Spring Boot；健康檢查端點為 `GET /api/health`。

Pages workflow 使用 Actions Variable `BACKEND_API_URL` 產生前端執行期設定 `frontend/public/config.js`。設定值是 API 根網址，不能以 `/api/v1/backtests` 結尾。
