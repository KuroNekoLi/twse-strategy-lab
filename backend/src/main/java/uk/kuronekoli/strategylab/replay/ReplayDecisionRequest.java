package uk.kuronekoli.strategylab.replay;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action;

/** Shares are whole units. WAIT accepts omitted or zero shares; BUY/SELL require positive shares. */
public record ReplayDecisionRequest(@NotNull Action action, Long shares) {
  BigDecimal quantity() {
    if (action == Action.WAIT) {
      if (shares != null && shares != 0) throw new IllegalArgumentException("觀望決策不可包含股數。");
      return BigDecimal.ZERO;
    }
    if (shares == null || shares <= 0) throw new IllegalArgumentException("買入或賣出時必須提供正整數股數。");
    return BigDecimal.valueOf(shares);
  }
}
