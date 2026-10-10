package uk.kuronekoli.strategylab.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.slf4j.LoggerFactory
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {
  private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)
  @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
  fun invalidRequest(exception: Exception): ResponseEntity<Map<String, String>> = ResponseEntity.badRequest().body(
    mapOf("code" to "INVALID_INPUT", "error" to "請求資料格式不正確，請確認回測參數後再試。")
  )

  @ExceptionHandler(BacktestException::class)
  fun domain(exception: BacktestException): ResponseEntity<Map<String, String>> = ResponseEntity.status(exception.status)
    .body(mapOf("code" to exception.code, "error" to exception.message.orEmpty()))

  @ExceptionHandler(IllegalArgumentException::class)
  fun badRequest(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> = ResponseEntity.badRequest().body(
    mapOf("code" to "INVALID_INPUT", "error" to (exception.message ?: "請求資料格式不正確。"))
  )

  @ExceptionHandler(NoMarketDataException::class)
  fun notFound(exception: NoMarketDataException): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.NOT_FOUND)
    .body(mapOf("code" to "NO_MARKET_DATA", "error" to exception.message.orEmpty()))

  @ExceptionHandler(NoResourceFoundException::class)
  fun resourceNotFound(exception: NoResourceFoundException): ResponseEntity<Map<String, String>> =
    ResponseEntity.status(HttpStatus.NOT_FOUND).body(
      mapOf("code" to "NOT_FOUND", "error" to "找不到要求的資源。")
    )

  @ExceptionHandler(Exception::class)
  fun upstream(exception: Exception, request: HttpServletRequest): ResponseEntity<Map<String, String>> {
    val incidentId = UUID.randomUUID().toString()
    log.error("Unhandled API failure: incidentId={}, method={}, path={}, exceptionType={}", incidentId, request.method, request.requestURI, exception.javaClass.name, exception)
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
      .body(mapOf("code" to "UPSTREAM_FAILURE", "error" to "無法完成行情研究計算，請稍後再試。", "incidentId" to incidentId))
  }

  class NoMarketDataException(message: String) : RuntimeException(message)
}
