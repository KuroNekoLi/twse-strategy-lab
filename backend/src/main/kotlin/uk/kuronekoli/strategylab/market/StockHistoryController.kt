package uk.kuronekoli.strategylab.market

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/stocks")
class StockHistoryController(private val service: StockHistoryService) {
    @GetMapping("/{symbol}/history")
    fun history(@PathVariable symbol: String, @RequestParam from: String, @RequestParam to: String) = service.history(symbol, from, to)
}
