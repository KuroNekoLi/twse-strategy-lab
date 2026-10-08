# M0 收盤資料研究引擎契約

版本：`m0-engine-v2.0.0`。範圍依 `產品規劃.md` 4.1、6、7、11.4、13.1；本文件描述目前程式實作，不代表 M1、正式可成交模型或對外發布驗收已完成。

## 研究與發布狀態

API 回傳 `status=LIMITED_RESEARCH`、`releaseStatus=BLOCKED`。公司行動、股利、交易日曆、停牌／上市狀態與行情授權未通過閘門，不能把有限研究結果稱為完整、可發布的市場回測。

現有來源只有日期與收盤價。`RAW_CLOSE_UNADJUSTED_V1` 保留原始收盤價；移除舊版對 0050 分割前价格除以四的硬編碼。未支援 point-in-time 公司行動、股利入帳、分割股數轉換或含息總報酬，亦不合成 open/high/low。

## 計算階段與責任

| 階段 | 實作 | 契約 |
|---|---|---|
| 輸入驗證 | `BacktestService` / `BacktestValidation` | 日期、標的、參數及金額／費率上限；直接引擎呼叫亦驗證帳務參數 |
| 行情載入 | `TwseMarketDataClient` | 逐月取得；非 OK、空月份、異常列不略過、不補價 |
| 資料完整性 | `BacktestValidation.bars` | 日期嚴格遞增、無重複、有限且正值的十進位收盤價；未接日曆，不能驗證應存在的交易日 |
| 訊號 | `StrategySignals` | 僅使用訊號日 T（含）之前資料 |
| 模擬交易與帳務 | `AccountingPortfolio` / `BacktestEngine` | 整數股、含費成本、剩餘現金、逐筆紀錄、每日對帳 |
| 績效 | `AccountingPortfolio.mark` / `BacktestEngine.finish` | 每日現金流中性 TWR、正規化最大回撤、明確 TWR 年化 |
| 身分 | `BacktestFingerprint` | 資料 SHA-256、已解決設定、所有版本；不宣稱已封存行情 |

## 訊號與成交代理時點

`NEXT_CLOSE_PROXY` / `next-close-proxy-v1.0.0`：T 收盤後形成策略訊號，在下一筆觀察到的日資料，使用該筆收盤作假設代理價格。此資料排序只保證「下一筆觀察資料」，沒有日曆時不能證明它就是實際下一交易日。這是研究模型，不能保證真實委託在該價成交；不模擬盤中價格、流動性、漲跌停、排隊或滑價。

交易紀錄保留 `signalDate` 與 `executionDate`。最後一筆的訊號沒有下一筆代理價格，禁止同日補成交；本版不輸出未成交的末日待辦訊號。

DCA 使用独立的預定時程 `DCA_FIRST_OBSERVED_BAR_OF_MONTH_V1`：首筆以初始資金投入，之後每月第一筆觀察資料在交易前加入月投入，使用該筆收盤代理價格買進。此時 `signalDate=null`，不代表根據該日未來收盤形成訊號。首筆不再額外加入月投入；即使月投入為零，月初仍可用之前剩餘現金買進。

## 策略精確版本

`strategyVersion=close-strategies-v2.0.0` 包含下列明確變體。C_t 為訊號日原始收盤價；SMA 使用最近 n 筆（含訊號日），完整暖機前禁止交易。策略空手時買進可負擔的最大整數股；持倉不加碼，賣出全部持倉。賣出條件僅在持倉時計算效果。

| key / 交易 reason | 買進 | 賣出 | 最少資料筆數（含一次執行） |
|---|---|---|---|
| `ma-crossover` / `SMA_CROSSING_V2` | SMA_fast(T) > SMA_slow(T)，且上一筆 fast ≤ slow | 當日 fast ≤ slow，且上一筆 fast > slow | slow + 2 |
| `rsi-reversion` / `RSI_SIMPLE_ROLLING_LEVEL_V1` | RSI < buy threshold | RSI > sell threshold | RSI window + 2 |
| `bollinger-reversion` / `BOLLINGER_POPULATION_LEVEL_V1` | C_T < SMA_n(T) − k·σ_population(T) | C_T ≥ SMA_n(T) | n + 1 |
| `breakout` / `PRIOR_CLOSE_BREAKOUT_LEVEL_V1` | C_T > max(C_(T-n), …, C_(T-1)) | C_T < 最近 n+1 筆（含 T）的 SMA | n + 2 |
| `drawdown-entry` / `PRIOR_252_DRAWDOWN_LEVEL_V1` | C_T ≤ max(C_(T-252), …, C_(T-1))·(1−d/100) | C_T ≥ 含買入費平均成本·(1+p/100) | 254 |

RSI 為簡單滾動 n 個價格變化的漲幅和 G／跌幅和 L：`100·G/(G+L)`；沒有跌幅且有漲幅時 100，全平時 50。**不是 Wilder 平滑，也不是門檻穿越版本。** 布林的標準差除以 n（母體）；買進為當日低於下軌的狀態，並非向下穿越。區間突破只使用歷史最高收盤，不稱為盤中 high 突破。回跌窗口排除 T；目標獲利依含費均價，並非實現淨收益保證。

## 帳務與成本

`whole-shares-cents-half-up-v1.0.0`：所有交易金額、資金流、現金、部位成本與日末資產採 BigDecimal，二位小數 HALF_UP；輸入資金最多二位小數。價格保留輸入十進位精度。指標／百分比可用有限數值計算，不能把金額帳務交給 double。

對 q 股、價格 P：

- gross = round(q·P, 2, HALF_UP)。
- fee = round(gross·commissionRate, 2, HALF_UP)，買賣都收。
- sell tax = round(gross·appliedSellTaxRate, 2, HALF_UP)，只在賣出收。
- 買進 cash′ = cash − gross − fee；positionCost′ = positionCost + gross + fee。
- 含費平均成本 = positionCost / shares，八位 HALF_UP；空手為零。
- 賣出 cash′ = cash + gross − fee − tax；shares′ = 0、positionCost′ = 0。
- equity = cash + round(shares·close, 2, HALF_UP)。

買進以實際捨入後費用尋找最大可負擔整數股，保留剩餘現金；不足一股時記錄 `SKIPPED_INSUFFICIENT_CASH`（quantity=0），不創造小數股。每次日末檢查現金、股數與部位成本非負及空手成本為零。對帳測試另依紀錄重建每筆 cash／shares／basis／equity。

無券商最低手續費、折扣、手續費的實際新台幣整元政策、零股成交驗證或歷史市場規則驗證。`research-estimated-tax-v1.0.0` 的預設稅率僅以 00 前綴估算 ETF 0.1%、其餘 0.3%；各標的實際採用值列在 `appliedSellTaxRates`，不能宣稱已正確識別全部商品或歷史稅率。

## 績效公式

`daily-twr-beginning-flow-v1.0.0`。每筆外部資金流 F_t 在該筆期間開始加入：首筆 F_0=initialCapital，之後月初 F_t=monthlyContribution，其他為零。E_t 為包含代理成交成本的日末資產。

首筆 factor_0 = E_0/F_0；之後 factor_t = E_t/(E_(t-1)+F_t)。因此首日買進費用立即成為報酬損失。此現金流時點是一項明確假设，沒有當日開盤價格可用來切割真實日內資金流。

- normalized NAV 起點為 1；N_t = 前一期 N·factor_t。
- timeWeightedReturn = (N_last−1)·100%。
- maxDrawdown = max_t(1−N_t / max(1, N_0,…,N_t))·100%，包含初始 NAV 高點，月投入不抬高此高點。
- contributed = 全部外部投入和；profit = endingValue−contributed。
- 保留舊欄位 totalReturn = profit/contributed·100%，名稱口徑為「投入損益比例」，不稱為 TWR。
- annualizedReturn = (N_last^(365.2425 / 實際首末日期日數)−1)·100%；`annualizationBasis=DAILY_TWR_ACT_365_2425`。**不是期末資產／總投入的 CAGR，也不是 252 日年化。**

沒有外部資金流且前後資產皆零時，該日回報分母為零，dailyReturn=null 並附 `DAILY_RETURN_ZERO_DENOMINATOR`；NAV 延續已為零的狀態。年化超出有限數值範圍則 annualizedReturn=null、`ANNUALIZATION_NUMERIC_RANGE`；TWR 超出範圍同樣 null、`TWR_NUMERIC_RANGE`。不輸出 Infinity／NaN，不以虛構 0 代替不可計算值。MWR、Sharpe、波動率、勝率等後續指標本版未支援。

## 重現識別與真實限制

每標的正規化文本為 UTF-8、LF：`normalized-close-dataset-v1`、symbol 各一行，再逐筆 `YYYY-MM-DD|close`；close 去尾零且無科學記號，每行（含最後一行）換行。其 SHA-256 識別實際使用且已篩選區間的 bars。跨標的 SHA-256 使用 `ordered-datasets-v1` 及請求順序的 `symbol|sha256` 各一行。回測 ID 另包含所有版本與固定欄位順序的已解決參數／各標的稅率。

相同捕捉資料與設定在本引擎可精確重跑；合成 fixtures 已在程式碼中保存。**live 回應只識別已消費資料，未封存原始快照**，所以 `reproducibilityStatus=IDENTIFIED_NOT_ARCHIVED`；同 URL、同参數及相同 hash 欄位不能保證多年後再取得相同行情。API 不新增原始行情快照匯出端點。

## 發布阻擋與未驗證範圍

股利、公司行動、日曆、停牌、缺漏原因及授權均 BLOCKED。空月份或錯誤列 fail closed；但一個非空月份內缺了應有的一天，本版沒有日曆可知，不能判斷其為假日或停牌，結果維持有限研究／發布阻擋。資料起訖與要求期間分開顯示，未默稱完整覆蓋；未來迄日 clamp 至今日亦必須有通知。

測試證据見 `../quality/GOLDEN_TEST_FIXTURES.md`。自動化與本地 HTTP 合成測試不等於 live 行情品質、實際成交、瀏覽器或對外發布驗證。
