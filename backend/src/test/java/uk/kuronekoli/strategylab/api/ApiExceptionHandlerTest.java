package uk.kuronekoli.strategylab.api;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();
  @Test void codedDomainErrorsPreserveExistingErrorFieldAndStatus() {
    var response = handler.domain(BacktestException.data("資料錯誤"));
    assertEquals(422, response.getStatusCode().value()); assertEquals("DATA_INTEGRITY_FAILED", response.getBody().get("code")); assertEquals("資料錯誤", response.getBody().get("error"));
    assertEquals("INVALID_INPUT", handler.invalidRequest(new Exception()).getBody().get("code"));
  }
  @Test void unexpectedUpstreamDetailsAreNotEchoedToPublicError() {
    var response = handler.upstream(new RuntimeException("internal credential or source body"));
    assertEquals(502, response.getStatusCode().value()); assertEquals("UPSTREAM_FAILURE", response.getBody().get("code")); assertFalse(response.getBody().get("error").contains("credential"));
  }
}
