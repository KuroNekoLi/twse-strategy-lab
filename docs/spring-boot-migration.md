# Spring Boot migration boundary

The browser talks only to versioned JSON endpoints. The current implementation uses a Cloudflare Worker route, but the request and response contract and the backtest engine are kept separate from the page so the API can move to Spring Boot without changing the interaction flow.

## Endpoint

`POST /api/v1/backtests`

Request:

```json
{
  "symbol": "0050",
  "symbols": ["0050"],
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

Response shape:

```json
{
  "symbol": "0050",
  "symbols": ["0050"],
  "from": "2010-01-04",
  "to": "2026-09-30",
  "tradingDays": 4200,
  "assets": [{"symbol":"0050","from":"2010-01-04","to":"2026-09-30","tradingDays":4200}],
  "dataSource": "臺灣證券交易所 STOCK_DAY",
  "assumptions": ["日收盤價", "不含配息", "月初投入", "不含滑價"],
  "results": [
    {
      "key": "0050-drawdown-entry",
      "name": "0050 · 回跌 20% 買 / 獲利 20% 賣",
      "endingValue": 0,
      "contributed": 0,
      "totalReturn": 0,
      "annualizedReturn": 0,
      "maxDrawdown": 0,
      "series": [{ "date": "2010-01", "value": 0 }]
    },
    {
      "key": "0050-dca",
      "name": "0050 · 定期定額",
      "endingValue": 0,
      "contributed": 0,
      "totalReturn": 0,
      "annualizedReturn": 0,
      "maxDrawdown": 0,
      "series": [{ "date": "2010-01", "value": 0 }]
    }
  ]
}
```

`lib/backtest-engine.ts` owns the deterministic strategy calculation. Its input is a validated request and ordered daily bars; it has no framework, network, database, or Worker dependency. `app/api/v1/backtests/route.ts` validates the contract, obtains TWSE bars, invokes the engine, and maps results to HTTP JSON.

`strategy` accepts `ma-crossover`, `rsi-reversion`, `bollinger-reversion`, `breakout`, or `drawdown-entry`. Every selected symbol returns two result rows: the selected strategy and the monthly DCA benchmark. `symbols` accepts one to three TWSE-listed demo symbols. Signals use the prior completed close and fill at the next close in the historical series. Percent thresholds are percentage points, while transaction cost rates remain decimals. When `useMarketTaxDefaults` is true, the service applies the configured listed-stock or ETF tax rate per symbol; otherwise it applies the supplied `sellTaxRate` to every selected symbol.

## Spring Boot mapping

- `BacktestController` implements `POST /api/v1/backtests` and returns the same response DTOs.
- Bean Validation on `BacktestRequest` replaces route-level validation.
- `TwseMarketDataClient` replaces the Worker fetch/cache adapter and returns `DailyBar` records.
- `BacktestService` ports the pure engine rules and produces the comparison response.
- `MarketDataCache` can use Caffeine or a persistent store without changing the HTTP contract.

Keep the API version, field names, date format, percent units, return assumptions, and error response stable. Before a migration, add contract examples from captured responses and compare both engines on the same fixed bar fixture.
