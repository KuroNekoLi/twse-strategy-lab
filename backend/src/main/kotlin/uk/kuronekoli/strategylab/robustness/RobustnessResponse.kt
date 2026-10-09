package uk.kuronekoli.strategylab.robustness

@JvmRecord data class RobustnessResponse(val baseId: String, val symbol: String, val requestedFrom: String, val requestedTo: String, val observedFrom: String, val observedTo: String, val sampleCount: Int, val status: String, val limitations: List<String>, val cases: List<RobustnessMatrix.CaseResult>, val aggregate: RobustnessMatrix.Aggregate)
