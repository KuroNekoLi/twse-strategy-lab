package uk.kuronekoli.strategylab.replay

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice(assignableTypes = [ReplaySessionController::class])
class ReplaySessionExceptionHandler {
    @ExceptionHandler(ReplaySessionException::class)
    fun session(exception: ReplaySessionException): ResponseEntity<Map<String, String>> = ResponseEntity.status(exception.status).body(mapOf("code" to exception.code, "error" to exception.message!!))
}
