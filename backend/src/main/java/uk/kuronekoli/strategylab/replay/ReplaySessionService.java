package uk.kuronekoli.strategylab.replay;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Decision;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Event;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.State;

/** Ephemeral, bounded in-process sessions over a fixed educational synthetic fixture. */
@Service
public class ReplaySessionService {
  public static final String CHALLENGE_ID = "synthetic-intro-v1";
  public static final int MAX_SESSIONS = 64;
  private static final Duration SESSION_TTL = Duration.ofMinutes(30);
  private static final BigDecimal STARTING_CASH = new BigDecimal("100000.00");
  private static final BigDecimal FEE_RATE = new BigDecimal("0.001425");
  private static final ReplayChallengeResponse.Challenge CHALLENGE = new ReplayChallengeResponse.Challenge(
      CHALLENGE_ID,
      "合成價格觀察練習",
      "完全合成的教學資料；不是任何真實證券或市場行情",
      "價格與日期均為固定合成樣本，不含股利、公司行動或真實交易日曆；不構成投資建議。每次決策後才會顯示下一個觀察點。練習只暫存在本服務程序記憶體，最多保留 30 分鐘。"
  );

  private final Clock clock;
  private final List<DailyBar> challengeBars;
  private final Map<String, Session> sessions = new LinkedHashMap<>(16, .75f, true);

  public ReplaySessionService() { this(Clock.systemUTC(), defaultBars()); }

  ReplaySessionService(Clock clock, List<DailyBar> challengeBars) {
    this.clock = clock;
    this.challengeBars = List.copyOf(challengeBars);
  }

  public synchronized ReplayChallengeResponse create(String challengeId) {
    purgeExpired();
    if (challengeId != null && !CHALLENGE_ID.equals(challengeId)) {
      throw new ReplaySessionException(HttpStatus.NOT_FOUND, "UNKNOWN_CHALLENGE", "找不到此教學練習。");
    }
    while (sessions.size() >= MAX_SESSIONS) {
      String eldest = sessions.keySet().iterator().next();
      sessions.remove(eldest);
    }
    String id = UUID.randomUUID().toString();
    Session session = new Session(new WalkForwardReplay(challengeBars, STARTING_CASH, FEE_RATE), clock.instant());
    sessions.put(id, session);
    return response(id, session);
  }

  public synchronized ReplayChallengeResponse get(String id) {
    return response(id, required(id));
  }

  public synchronized ReplayChallengeResponse decide(String id, ReplayDecisionRequest request) {
    Session session = required(id);
    if (request == null || request.action() == null) throw new IllegalArgumentException("請提供 WAIT、BUY 或 SELL 決策。");
    session.replay.decide(new Decision(request.action(), request.quantity()));
    session.lastTouched = clock.instant();
    return response(id, session);
  }

  private Session required(String id) {
    purgeExpired();
    Session session = sessions.get(id);
    if (session == null) throw new ReplaySessionException(HttpStatus.NOT_FOUND, "REPLAY_SESSION_NOT_FOUND", "練習不存在或已逾期，請重新開始。");
    session.lastTouched = clock.instant();
    return session;
  }

  private void purgeExpired() {
    Instant expiry = clock.instant().minus(SESSION_TTL);
    sessions.entrySet().removeIf(entry -> !entry.getValue().lastTouched.isAfter(expiry));
  }

  private ReplayChallengeResponse response(String id, Session session) {
    State state = session.replay.state();
    var observation = state.observation() == null ? null : new ReplayChallengeResponse.Observation(
        state.observation().date(), state.observation().close());
    List<ReplayChallengeResponse.DecisionEvent> events = state.events().stream().map(this::event).toList();
    return new ReplayChallengeResponse(id, CHALLENGE, observation,
        new ReplayChallengeResponse.Portfolio(state.cash(), state.shares(), state.events().size(), state.complete()), events);
  }

  private ReplayChallengeResponse.DecisionEvent event(Event event) {
    return new ReplayChallengeResponse.DecisionEvent(event.date(), event.observedClose(), event.action().name(),
        event.quantity(), event.cashAfter(), event.sharesAfter());
  }

  private static List<DailyBar> defaultBars() {
    // The dates and prices below are invented constants for an educational exercise; they are not sourced from a market.
    return List.of(
        new DailyBar(LocalDate.of(2031, 1, 6), new BigDecimal("100.00")),
        new DailyBar(LocalDate.of(2031, 1, 7), new BigDecimal("96.00")),
        new DailyBar(LocalDate.of(2031, 1, 8), new BigDecimal("103.00")),
        new DailyBar(LocalDate.of(2031, 1, 9), new BigDecimal("101.00")),
        new DailyBar(LocalDate.of(2031, 1, 12), new BigDecimal("109.00")));
  }

  private static final class Session {
    private final WalkForwardReplay replay;
    private Instant lastTouched;
    private Session(WalkForwardReplay replay, Instant lastTouched) { this.replay = replay; this.lastTouched = lastTouched; }
  }
}
