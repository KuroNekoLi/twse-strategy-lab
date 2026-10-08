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
    return ResponseEntity.badRequest().body(Map.of("error", "請求資料格式不正確，請確認回測參數後再試。"));
  }
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
  }
  @ExceptionHandler(NoMarketDataException.class)
  ResponseEntity<Map<String, String>> notFound(NoMarketDataException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
  }
  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, String>> upstream(Exception exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", exception.getMessage() == null ? "回測服務發生錯誤。" : exception.getMessage()));
  }
  public static class NoMarketDataException extends RuntimeException {
    public NoMarketDataException(String message) { super(message); }
  }
}
