package uk.kuronekoli.strategylab.market

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.MediaType
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/api/v1/stocks")
class StockHistoryController(private val service: StockHistoryService, private val liveQuotes: LiveQuoteStreamService) {
    @GetMapping("/{symbol}/history")
    fun history(
        @PathVariable symbol: String,
        @RequestParam from: String,
        @RequestParam to: String,
        @RequestParam(defaultValue = "1d") interval: String,
    ) = service.history(symbol, from, to, interval)

    @GetMapping("/{symbol}/live", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun live(@PathVariable symbol: String): SseEmitter = liveQuotes.stream(symbol)
}
