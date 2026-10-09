package uk.kuronekoli.strategylab.replay

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/replay/sessions")
class ReplaySessionController(private val service: ReplaySessionService) {
    @PostMapping fun create(@RequestBody(required = false) request: CreateReplayRequest?): ReplayChallengeResponse = service.create(request?.challengeId)
    @GetMapping("/{sessionId}") fun get(@PathVariable sessionId: String): ReplayChallengeResponse = service.get(sessionId)
    @PostMapping("/{sessionId}/decisions") fun decide(@PathVariable sessionId: String, @Valid @RequestBody request: ReplayDecisionRequest): ReplayChallengeResponse = service.decide(sessionId, request)
    data class CreateReplayRequest(val challengeId: String?)
}
