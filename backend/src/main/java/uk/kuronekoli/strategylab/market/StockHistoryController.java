package uk.kuronekoli.strategylab.market;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stocks")
public final class StockHistoryController {
  private final StockHistoryService service;
  public StockHistoryController(StockHistoryService service) { this.service = service; }
  @GetMapping("/{symbol}/history")
  public StockHistoryResponse history(@PathVariable String symbol, @RequestParam String from, @RequestParam String to) {
    return service.history(symbol, from, to);
  }
}
