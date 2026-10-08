package uk.kuronekoli.strategylab.replay;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.kuronekoli.strategylab.market.DailyBar;
import uk.kuronekoli.strategylab.replay.ReplaySessionStore.DecisionRecord;
import uk.kuronekoli.strategylab.replay.ReplaySessionStore.SessionRecord;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Decision;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Event;
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.State;

/** Persists only user decisions; derived state is rebuilt from the fixed synthetic fixture. */
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
      "價格與日期均為固定合成樣本，不含股利、公司行動或真實交易日曆；不構成投資建議。每次決策後才會顯示下一個觀察點。匿名練習最多保留 30 分鐘。"
  );

  private final ReplaySessionStore store;
  private final Clock clock;
  private final List<DailyBar> challengeBars;

  @Autowired
  public ReplaySessionService(ReplaySessionStore store) {
    this(store, Clock.systemUTC(), defaultBars());
  }

  ReplaySessionService(Clock clock, List<DailyBar> challengeBars) {
    this(new MemoryStore(), clock, challengeBars);
  }

  ReplaySessionService(ReplaySessionStore store, Clock clock, List<DailyBar> challengeBars) {
    this.store = store;
    this.clock = clock;
    this.challengeBars = List.copyOf(challengeBars);
  }

  @Transactional
  public synchronized ReplayChallengeResponse create(String challengeId) {
    purgeExpired();
    if (challengeId != null && !CHALLENGE_ID.equals(challengeId)) {
      throw new ReplaySessionException(HttpStatus.NOT_FOUND, "UNKNOWN_CHALLENGE", "找不到此教學練習。");
    }
    while (store.oldestFirst().size() >= MAX_SESSIONS) store.delete(store.oldestFirst().get(0).id());
    Instant now = clock.instant();
    String id = UUID.randomUUID().toString();
    SessionRecord session = new SessionRecord(id, CHALLENGE_ID, now, now, now.plus(SESSION_TTL));
    store.save(session);
    return response(id, replayFor(id));
  }

  @Transactional
  public synchronized ReplayChallengeResponse get(String id) {
    SessionRecord session = required(id);
    Instant now = clock.instant();
    store.save(new SessionRecord(session.id(), session.challengeId(), session.createdAt(), now, now.plus(SESSION_TTL)));
    return response(id, replayFor(id));
  }

  @Transactional
  public synchronized ReplayChallengeResponse decide(String id, ReplayDecisionRequest request) {
    SessionRecord session = required(id);
    if (request == null || request.action() == null) throw new IllegalArgumentException("請提供 WAIT、BUY 或 SELL 決策。");
    BigDecimal quantity = request.quantity();
    WalkForwardReplay replay = replayFor(id);
    replay.decide(new Decision(request.action(), quantity));
    Instant now = clock.instant();
    List<DecisionRecord> existing = store.decisions(id);
    Long shares = request.action() == Action.WAIT ? null : request.shares();
    store.appendDecision(id, new DecisionRecord(existing.size(), request.action(), shares, now));
    store.save(new SessionRecord(session.id(), session.challengeId(), session.createdAt(), now, now.plus(SESSION_TTL)));
    return response(id, replayFor(id));
  }

  private SessionRecord required(String id) {
    purgeExpired();
    SessionRecord session = store.find(id).orElse(null);
    if (session == null || !session.expiresAt().isAfter(clock.instant())) {
      if (session != null) store.delete(id);
      throw new ReplaySessionException(HttpStatus.NOT_FOUND, "REPLAY_SESSION_NOT_FOUND", "練習不存在或已逾期，請重新開始。");
    }
    return session;
  }

  private void purgeExpired() {
    Instant now = clock.instant();
    store.findExpired(now).forEach(session -> store.delete(session.id()));
  }

  private WalkForwardReplay replayFor(String id) {
    List<Decision> decisions = store.decisions(id).stream()
        .map(row -> new Decision(row.action(), row.action() == Action.WAIT ? BigDecimal.ZERO : BigDecimal.valueOf(row.shares())))
        .toList();
    WalkForwardReplay replay = new WalkForwardReplay(challengeBars, STARTING_CASH, FEE_RATE);
    decisions.forEach(replay::decide);
    return replay;
  }

  private ReplayChallengeResponse response(String id, WalkForwardReplay replay) {
    State state = replay.state();
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

  /** Keeps deterministic standalone MVC contract tests independent of a database. */
  private static final class MemoryStore implements ReplaySessionStore {
    private final Map<String, SessionRecord> sessions = new LinkedHashMap<>(16, .75f, true);
    private final Map<String, List<DecisionRecord>> decisions = new LinkedHashMap<>();
    @Override public SessionRecord save(SessionRecord row) { sessions.put(row.id(), row); return row; }
    @Override public Optional<SessionRecord> find(String id) { return Optional.ofNullable(sessions.get(id)); }
    @Override public List<SessionRecord> findExpired(Instant now) {
      List<SessionRecord> expired = sessions.values().stream().filter(row -> !row.expiresAt().isAfter(now)).toList();
      expired.forEach(row -> delete(row.id())); return expired;
    }
    @Override public List<SessionRecord> oldestFirst() { return new ArrayList<>(sessions.values()); }
    @Override public List<DecisionRecord> decisions(String id) { return List.copyOf(decisions.getOrDefault(id, List.of())); }
    @Override public void appendDecision(String id, DecisionRecord row) { decisions.computeIfAbsent(id, ignored -> new ArrayList<>()).add(row); }
    @Override public void delete(String id) { sessions.remove(id); decisions.remove(id); }
  }
}
