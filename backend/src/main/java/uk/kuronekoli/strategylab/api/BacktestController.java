package uk.kuronekoli.strategylab.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.kuronekoli.strategylab.backtest.BacktestService;

@RestController
@RequestMapping("/api/v1/backtests")
public class BacktestController {
  private final BacktestService service;
  public BacktestController(BacktestService service) { this.service = service; }
  @PostMapping public BacktestResponse backtest(@Valid @RequestBody BacktestRequest request) { return service.run(request); }
}
