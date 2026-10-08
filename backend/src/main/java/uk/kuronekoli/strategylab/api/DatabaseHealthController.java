package uk.kuronekoli.strategylab.api;

import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseHealthController {
  private final JdbcTemplate jdbc;

  public DatabaseHealthController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping("/api/health/database")
  public ResponseEntity<Map<String, String>> database() {
    try {
      Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
      if (Integer.valueOf(1).equals(result)) {
        return ResponseEntity.ok(Map.of("status", "ok", "database", "ok"));
      }
    } catch (DataAccessException exception) {
      // Keep connection details and credentials out of the public health response.
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("status", "unavailable", "database", "unavailable"));
  }
}
