package uk.kuronekoli.strategylab.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/instruments")
public final class InstrumentCatalogController {
  private final InstrumentCatalogService service;
  public InstrumentCatalogController(InstrumentCatalogService service) { this.service = service; }
  @GetMapping public InstrumentCatalogResponse search(@RequestParam String query, @RequestParam(defaultValue = "20") int limit) {
    return service.search(query, limit);
  }
}
