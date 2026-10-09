package uk.kuronekoli.strategylab.replay

import org.springframework.http.HttpStatus

class ReplaySessionException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)
