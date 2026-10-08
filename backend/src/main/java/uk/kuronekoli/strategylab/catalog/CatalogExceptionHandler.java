package uk.kuronekoli.strategylab.catalog;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = InstrumentCatalogController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CatalogExceptionHandler {
  @ExceptionHandler(CatalogException.class)
  ResponseEntity<Map<String, String>> catalog(CatalogException error) {
    return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "error", error.getMessage()));
  }
  @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  ResponseEntity<Map<String, String>> invalidQuery(Exception error) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_CATALOG_QUERY", "error", "請提供代碼或名稱，以及有效的查詢筆數。"));
  }
}
