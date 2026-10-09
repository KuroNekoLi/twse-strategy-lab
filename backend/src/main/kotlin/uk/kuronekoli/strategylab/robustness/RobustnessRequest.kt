package uk.kuronekoli.strategylab.robustness

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import uk.kuronekoli.strategylab.api.BacktestRequest

/** One base configuration and a bounded list of explicit sensitivity cases. */
data class RobustnessRequest(@field:NotBlank val baseId: String, @field:NotNull @field:Valid val base: BacktestRequest, @field:NotEmpty @field:Valid val variants: List<RobustnessMatrix.Variant>)
