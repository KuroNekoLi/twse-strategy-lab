package uk.kuronekoli.strategylab.replay

import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action

data class ReplayDecisionRequest(@field:NotNull val action: Action?, val shares: Long?) {
    fun quantity(): BigDecimal {
        if (action == Action.WAIT) { require(shares == null || shares == 0L) { "觀望決策不可包含股數。" }; return BigDecimal.ZERO }
        require(shares != null && shares > 0) { "買入或賣出時必須提供正整數股數。" }
        return BigDecimal.valueOf(shares)
    }
}
