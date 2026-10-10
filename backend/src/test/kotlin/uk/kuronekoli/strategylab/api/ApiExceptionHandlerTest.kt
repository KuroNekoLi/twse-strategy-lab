package uk.kuronekoli.strategylab.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.resource.NoResourceFoundException

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
    val request = MockHttpServletRequest("POST", "/api/v1/backtests")
    val response = handler.upstream(RuntimeException("internal credential or source body"), request)
    assertEquals(502, response.statusCode.value())
    assertEquals("UPSTREAM_FAILURE", response.body?.get("code"))
    assertFalse(response.body?.get("error").orEmpty().contains("credential"))
    assertTrue(response.body?.get("incidentId").orEmpty().matches(Regex("[0-9a-f-]{36}")))
  }

  @Test
  fun missingStaticResourceIsAPlainNotFoundInsteadOfAnUpstreamFailure() {
    val exception = NoResourceFoundException(HttpMethod.GET, "/favicon.ico", "/favicon.ico")
    val response = handler.resourceNotFound(exception)

    assertEquals(404, response.statusCode.value())
    assertEquals("NOT_FOUND", response.body?.get("code"))
    assertEquals("找不到要求的資源。", response.body?.get("error"))
    assertFalse(response.body?.containsKey("incidentId") == true)
  }
}
