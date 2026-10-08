package uk.kuronekoli.strategylab.replay;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/replay/sessions")
public class ReplaySessionController {
  private final ReplaySessionService service;
  public ReplaySessionController(ReplaySessionService service) { this.service = service; }

  @PostMapping
  public ReplayChallengeResponse create(@RequestBody(required = false) CreateReplayRequest request) {
    return service.create(request == null ? null : request.challengeId());
  }

  @GetMapping("/{sessionId}")
  public ReplayChallengeResponse get(@PathVariable String sessionId) { return service.get(sessionId); }

  @PostMapping("/{sessionId}/decisions")
  public ReplayChallengeResponse decide(@PathVariable String sessionId, @Valid @RequestBody ReplayDecisionRequest request) {
    return service.decide(sessionId, request);
  }

  public record CreateReplayRequest(String challengeId) {}
}
