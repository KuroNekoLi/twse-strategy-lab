# M0 Golden Fixtures 與阻擋案例

合成日期／收盤數列保存在 `backend/src/test/java/uk/kuronekoli/strategylab/backtest/BacktestEngineGoldenTest.java`。期望帳務數值先依下列手算寫定，測試不以引擎輸出產生期望值。各日合成數列可含週末：它們是確定性的數學 bars，**不是已驗證的台股交易日曆**。

初始資金預設 1000、月投入 0、成本 0、MA fast=2/slow=3；未特別列出的例外使用此設定。

## 手算基準

| ID | 合成數列與條件 | 獨立期望 | 自動化證據 |
|---|---|---|---|
| G01 上漲 | 100,110,120,130,140 | DCA 10 股，期末1400、TWR40%、MDD0；MA 從未穿越，空手1000 | `risingPricesDoNotInventCrossingAndDcaGrowsFortyPercent` |
| G02 下跌 | 100,90,80,70,60 | DCA 10股，期末600、TWR−40%、MDD40% | `fallingAndFlatPricesHaveHandwrittenReturns` |
| G03 橫盤 | 五筆100 | 期末1000、TWR0%、年化0% | 同 G02 |
| G04 黃金／死亡交叉 | 100,100,100,120,110,80,90,100 | Jan4 黃金交叉、Jan5 9@110 cash10；Jan6 死亡交叉、Jan7 9@90→cash820；末日交叉無下一筆不可補成交 | `goldenAndDeathCrossExecuteOnlyAtNextObservedClose` |
| G05 費用、交易稅、均價 | 100,100,100,120,100,80,110；fee1%、tax0.3% | 買9@100 gross900 fee9 cash91 均價101；賣9@110 gross990 fee9.90 tax2.97；cash=91+990−9.90−2.97=1068.13；profit68.13、TWR6.813% | `feesTaxResidualCashAndFeeInclusiveCostMatchIndependentArithmetic` |
| G06 首日成本／HALF_UP | 五筆100，fee1%；另 gross10、費率0.0005 | DCA 9股+cash91→equity991，首日TWR−0.9%、MDD0.9%；10×.0005=.005→.01；買10.01後cash0，賣 gross10扣fee.01/tax.01→9.98 | `feeRoundingUsesCentsHalfUpAndFirstDayCostIsTwrLoss` |
| G07 真實捨入後可負擔 | cash10、price10、fee.0001 | fee.001→0.00，能買整數一股；不能用未捨入理論費判斷買不起 | `roundedAffordabilityCanBuyAWholeShareWithoutTheoreticalFractionalFee` |
| G08 現金不足 | cash50，100,100,100,120,100 | DCA 及策略買進意圖 quantity0、SKIPPED_INSUFFICIENT_CASH，cash50；不產生0.5股 | `cashShortageCreatesAnExplicitSkippedIntentAndNoFractionalShares` |
| G09 月投入不隱藏回撤 | Jan27..Feb2：100,100,100,90,90,90,90；月投入1000 | 原10股降至900；Feb1加1000買11@90留cash10、21股值1890，equity1900／contributed2000；投入損益−5%，TWR−10%、MDD10%；Feb1因1900/(900+1000)=1，NAV仍.9；年化(.9^(365.2425/6)−1)×100 | `monthlyDepositCannotHideTenPercentDrawdown` |
| G10 RSI simple level | n2，100,90,80,70,100,110 | Jan3 RSI0買意圖；Jan4 14@70 cash20；Jan5 RSI=100·30/(30+10)=75賣意圖；Jan6賣14@110→1560 | `rsiSimpleRollingLevelHasKnownBuyAndSellDates` |
| G11 布林母體／狀態 | n3、k.5，100,90,80,90,100 | Jan3 mean90、σ=sqrt(200/3)、lower≈85.9175，80以下軌；Jan4買11@90 cash10且90≥mean86.6667；Jan5賣11@100→1110 | `bollingerUsesPopulationDeviationAndLevelThreshold` |
| G12 排除當日突破 | n2，100,100,120,130,80,90 | Jan3 120>前2筆high100；Jan4買7@130 cash90；Jan5 80<mean(120,130,80)=110；Jan6賣7@90→720 | `breakoutHighExcludesSignalDayAndExitUsesItsMean` |
| G13 回跌前252 | index0=200、1..251=100、252=150、253=100、254=130、255=110 | index252前252高200保留index0；150≤160觸發；index253買10@100；index254 130≥120觸發；index255賣10@110→1100。含訊號日的錯窗口會漏掉index0而錯過買訊號 | `drawdownPrior252ExcludesSignalDayAndRetainsOldestPriorHigh` |
| G14 不足／異常資料 | MA僅4筆；0/負價/null/重複/逆序 | INSUFFICIENT_DATA 或 DATA_INTEGRITY_FAILED；禁止靜默補價／排序／去重 | `insufficientDuplicateUnorderedAndNonpositiveBarsFailClosed` |
| G15 決定性／前視偏誤 | 同份已捕捉 bars 重跑，後接10000、1的未來數列 | 相同輸入 record 完全一致；五策略及 DCA 的過去每日帳務／交易不隨未來 suffix 改變 | `identicalCapturedBarsReplayExactlyAndFutureSuffixCannotChangePast` / `futureSuffixInvarianceAppliesToEveryVersionedStrategy` |
| G16 帳本對帳 | 跨Jan/Feb並有交叉買賣、fee1% tax.3%、月100 | 由外部投入+gross/fee/tax獨立重建 cash、shares、basis、日末equity、累計投入；profit=ending−contributed | `ledgerCanBeRebuiltFromIndependentCashAndPositionEquations` |
| G17 不可計算指標 | 1,1,1,1,1e9；另 gross.01、fee.99 歸零帳戶 | 短期年化超有限範圍→null+ANNUALIZATION_NUMERIC_RANGE，JSON無Infinity/NaN；零分母dailyReturn→null+DAILY_RETURN_ZERO_DENOMINATOR | `extremeShortPeriodAnnualizationIsUnavailableAndNeverInfinityJson` / `zeroEquityFollowingRoundedSellCostsMakesSubsequentDailyReturnUnavailable` |

## 其他自動化路徑

- `TwseMarketDataClientTest`：合成 provider JSON 的 ROC日期／千分位十進位；非OK、缺stat、空月份、不合法列／缺價 `--`、錯月份、重複與逆序拒絕。沒有網路呼叫。
- `BacktestServiceTest`：要求／實際期間差異、未來迄日clamp、明示BLOCKED限制、SHA-256重現識別；參數或資料改變造成ID不同；等價十進位呈現dataset hash一致；null／負值／超限／非有限值／非法費率／浮點殘值拒絕；0.001425合法；0050不再硬編碼調整。
- `ApiExceptionHandlerTest`：穩定code與既有error欄位、公開错误不洩漏未預期上游內容。
- `BacktestHttpContractTest`：真實 SpringBoot 4.1.1 + Tomcat 隨機本地埠啟動，使用 `@Primary` 合成來源；兼容POST回傳十進位number、next-close與BLOCKED；DCA1000@100、fee.001425→9股、fee1.28、equity998.72；null金額／壞JSON返回400 INVALID_INPUT。

## PRD 13.1 尚未支援的案例

以下不能計入 PASS。拒絕錯誤月資料的測試不等於支援停牌、日曆或公司行動。

| PRD 案例 | 狀態 | 目前行為 | 解鎖條件 |
|---|---|---|---|
| 假日／停牌 | BLOCKED | 無交易日曆、instrument lifecycle或停牌事件；下一筆觀察資料不證明下一交易日；市場狀態UNKNOWN | 授權日曆與停牌資料、明确不成交／排程規則、獨立期望測試 |
| 股利入帳 | BLOCKED | 不入帳、不再投入，DIVIDENDS_UNSUPPORTED；不能稱含息總報酬 | 股利資料、可知時點、除息／發放日事件與帳務測試 |
| 股票分割／其他公司行動 | BLOCKED | 原始未調整收盤、不變更股數；CORPORATE_ACTIONS_UNSUPPORTED；0050舊特例已移除 | 版本化point-in-time公司行動與持倉成本事件、独立期望測試 |
| 交易後立即資料缺漏 | BLOCKED（原因判別） | 空月份／錯列fail closed已有測試；非空月份內遺失應有交易日無法與假日／停牌區分，不能默稱完整成功 | 授權交易日曆、完整性比對、missing-after-fill拒絕／停算策略與測試 |

行情授權、歷史稅率與來源完整性亦阻擋公開發布。合成快照保存使本地fixture可重跑；live行情只有IDENTIFIED_NOT_ARCHIVED識別，未保證未來可重取或重播。

## 實際檢查

在 Java 17.0.15、Spring Boot 4.1.1 下，2026-10-09 已執行 Maven test／package；最終數量與結果以本次 `backend/target/surefire-reports` 及團隊驗收記錄為準。測試結果不能替代獨立程式審查、瀏覽器驗證、live行情覆蓋或發布驗收。

重跑（專案根目錄；使用repo-local Maven、不安裝系統套件）：

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/amazon-corretto-17.jdk/Contents/Home .tools/apache-maven-3.9.11/bin/mvn -B -Dmaven.repo.local=/Users/linli/dev_spring-boot/twse-strategy-lab/.cache/m2 -f backend/pom.xml package
```
