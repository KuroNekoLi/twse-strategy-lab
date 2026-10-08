package uk.kuronekoli.strategylab.api;

/** Stable public error codes; messages contain no upstream payload or secret. */
public class BacktestException extends RuntimeException {
  private final String code;
  private final int status;
  public BacktestException(String code, String message, int status) {
    super(message); this.code = code; this.status = status;
  }
  public String code() { return code; }
  public int status() { return status; }
  public static BacktestException input(String message) { return new BacktestException("INVALID_INPUT", message, 400); }
  public static BacktestException data(String message) { return new BacktestException("DATA_INTEGRITY_FAILED", message, 422); }
  public static BacktestException upstream(String message) { return new BacktestException("UPSTREAM_DATA_UNAVAILABLE", message, 502); }
}
