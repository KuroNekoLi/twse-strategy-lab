package uk.kuronekoli.strategylab.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class ApiExceptionHandlerTest {
  private val handler = ApiExceptionHandler()

  @Test
  fun codedDomainErrorsPreserveExistingErrorFieldAndStatus() {
    val response = handler.domain(BacktestException.data("資料錯誤"))
    assertEquals(422, response.statusCode.value())
    assertEquals("DATA_INTEGRITY_FAILED", response.body?.get("code"))
    assertEquals("資料錯誤", response.body?.get("error"))
    assertEquals("INVALID_INPUT", handler.invalidRequest(Exception()).body?.get("code"))
  }

  @Test
  fun unexpectedUpstreamDetailsAreNotEchoedToPublicError() {
    val response = handler.upstream(RuntimeException("internal credential or source body"))
    assertEquals(502, response.statusCode.value())
    assertEquals("UPSTREAM_FAILURE", response.body?.get("code"))
    assertFalse(response.body?.get("error").orEmpty().contains("credential"))
  }
}
