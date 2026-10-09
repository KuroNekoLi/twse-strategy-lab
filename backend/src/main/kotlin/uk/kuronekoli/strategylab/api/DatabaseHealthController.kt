package uk.kuronekoli.strategylab.api

import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DatabaseHealthController(private val jdbc: JdbcTemplate) {
  @GetMapping("/api/health/database")
  fun database(): ResponseEntity<Map<String, String>> {
    try {
      val result = jdbc.queryForObject("SELECT 1", Int::class.java)
      if (result == 1) return ResponseEntity.ok(mapOf("status" to "ok", "database" to "ok"))
    } catch (_: DataAccessException) {
      // Keep connection details and credentials out of the public health response.
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
      .body(mapOf("status" to "unavailable", "database" to "unavailable"))
  }
}
