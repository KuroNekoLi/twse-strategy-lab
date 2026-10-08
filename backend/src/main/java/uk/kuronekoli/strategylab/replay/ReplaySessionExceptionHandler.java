package uk.kuronekoli.strategylab.replay;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ReplaySessionController.class)
public class ReplaySessionExceptionHandler {
  @ExceptionHandler(ReplaySessionException.class)
  ResponseEntity<Map<String, String>> session(ReplaySessionException exception) {
    return ResponseEntity.status(exception.status()).body(Map.of("code", exception.code(), "error", exception.getMessage()));
  }
}
