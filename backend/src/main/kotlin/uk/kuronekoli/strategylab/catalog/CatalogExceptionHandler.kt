package uk.kuronekoli.strategylab.catalog

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(assignableTypes = [InstrumentCatalogController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
class CatalogExceptionHandler {
    @ExceptionHandler(CatalogException::class)
    fun catalog(error: CatalogException): ResponseEntity<Map<String, String>> = ResponseEntity.status(error.status).body(mapOf("code" to error.code, "error" to (error.message ?: "")))
    @ExceptionHandler(MissingServletRequestParameterException::class, MethodArgumentTypeMismatchException::class)
    fun invalidQuery(error: Exception): ResponseEntity<Map<String, String>> = ResponseEntity.badRequest().body(mapOf("code" to "INVALID_CATALOG_QUERY", "error" to "請提供代碼或名稱，以及有效的查詢筆數。"))
}
