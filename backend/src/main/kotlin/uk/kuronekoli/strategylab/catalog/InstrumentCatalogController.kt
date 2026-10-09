package uk.kuronekoli.strategylab.catalog

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/instruments")
class InstrumentCatalogController(private val service: InstrumentCatalogService) {
    @GetMapping fun search(@RequestParam query: String, @RequestParam(defaultValue = "20") limit: Int) = service.search(query, limit)
}
