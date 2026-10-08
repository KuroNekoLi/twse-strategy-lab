package uk.kuronekoli.strategylab.robustness;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import uk.kuronekoli.strategylab.api.BacktestRequest;

/** One base configuration and a bounded list of explicit sensitivity cases. */
public record RobustnessRequest(
    @NotBlank String baseId,
    @NotNull @Valid BacktestRequest base,
    @NotEmpty @Valid List<RobustnessMatrix.Variant> variants) {}
