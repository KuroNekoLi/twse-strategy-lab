package uk.kuronekoli.strategylab.robustness

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/robustness-matrices")
class RobustnessController(private val service: RobustnessService) {
    @PostMapping fun analyze(@Valid @RequestBody request: RobustnessRequest): RobustnessResponse = service.run(request)
}
