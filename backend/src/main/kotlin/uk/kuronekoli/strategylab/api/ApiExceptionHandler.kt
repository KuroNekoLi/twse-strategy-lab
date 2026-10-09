package uk.kuronekoli.strategylab.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
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

  @ExceptionHandler(Exception::class)
  fun upstream(exception: Exception): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.BAD_GATEWAY)
    .body(mapOf("code" to "UPSTREAM_FAILURE", "error" to "無法完成行情研究計算，請稍後再試。"))

  class NoMarketDataException(message: String) : RuntimeException(message)
}
