package uk.kuronekoli.strategylab.replay;

import org.springframework.http.HttpStatus;

public final class ReplaySessionException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ReplaySessionException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() { return status; }
  public String code() { return code; }
}
