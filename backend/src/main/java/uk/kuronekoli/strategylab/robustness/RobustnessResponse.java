package uk.kuronekoli.strategylab.robustness;

import java.util.List;

public record RobustnessResponse(String baseId, String symbol, String requestedFrom, String requestedTo,
    String observedFrom, String observedTo, int sampleCount, String status,
    List<String> limitations, List<RobustnessMatrix.CaseResult> cases,
    RobustnessMatrix.Aggregate aggregate) {}
