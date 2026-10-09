package uk.kuronekoli.strategylab.api

/** Stable public error codes; messages contain no upstream payload or secret. */
class BacktestException(val code: String, message: String, val status: Int) : RuntimeException(message) {
  companion object {
    @JvmStatic fun input(message: String) = BacktestException("INVALID_INPUT", message, 400)
    @JvmStatic fun data(message: String) = BacktestException("DATA_INTEGRITY_FAILED", message, 422)
    @JvmStatic fun upstream(message: String) = BacktestException("UPSTREAM_DATA_UNAVAILABLE", message, 502)
  }
}
