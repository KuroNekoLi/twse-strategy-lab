package uk.kuronekoli.strategylab.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT", "error", "請求資料格式不正確，請確認回測參數後再試。"));
  }
  @ExceptionHandler(BacktestException.class)
  ResponseEntity<Map<String, String>> domain(BacktestException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("code", exception.code(), "error", exception.getMessage()));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("code", "INVALID_INPUT", "error", exception.getMessage() == null ? "請求資料格式不正確。" : exception.getMessage()));
  }
  @ExceptionHandler(NoMarketDataException.class)
  ResponseEntity<Map<String, String>> notFound(NoMarketDataException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("code", "NO_MARKET_DATA", "error", exception.getMessage()));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> upstream(Exception exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("code", "UPSTREAM_FAILURE", "error", "無法完成行情研究計算，請稍後再試。"));
  }
  public static class NoMarketDataException extends RuntimeException {
    public NoMarketDataException(String message) { super(message); }
  }
}
