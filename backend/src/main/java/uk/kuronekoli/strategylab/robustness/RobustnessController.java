package uk.kuronekoli.strategylab.robustness;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/robustness-matrices")
public class RobustnessController {
  private final RobustnessService service;
  public RobustnessController(RobustnessService service) { this.service = service; }
  @PostMapping public RobustnessResponse analyze(@Valid @RequestBody RobustnessRequest request) {
    return service.run(request);
  }
}
