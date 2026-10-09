package uk.kuronekoli.strategylab.api

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.kuronekoli.strategylab.backtest.BacktestService

@RestController
@RequestMapping("/api/v1/backtests")
class BacktestController(private val service: BacktestService) {
  @PostMapping
  fun backtest(@Valid @RequestBody request: BacktestRequest): BacktestResponse = service.run(request)
}
