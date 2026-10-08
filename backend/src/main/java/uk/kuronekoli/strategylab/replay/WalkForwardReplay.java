package uk.kuronekoli.strategylab.replay;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import uk.kuronekoli.strategylab.market.DailyBar;

/** In-memory, close-only exercise. Future bars remain private and are never returned by this type. */
public final class WalkForwardReplay {
  public enum Action { WAIT, BUY, SELL }

  public record Observation(LocalDate date, BigDecimal close) {}
  public record Decision(Action action, BigDecimal quantity) {
    public Decision {
      Objects.requireNonNull(action, "action");
      Objects.requireNonNull(quantity, "quantity");
      if (quantity.signum() < 0) throw new IllegalArgumentException("數量不可為負數。");
      if (action == Action.WAIT && quantity.signum() != 0) throw new IllegalArgumentException("觀望數量必須為零。");
      if (action != Action.WAIT && quantity.signum() <= 0) throw new IllegalArgumentException("買賣數量必須大於零。");
    }
  }
  public record Event(LocalDate date, BigDecimal observedClose, Action action, BigDecimal quantity,
      BigDecimal gross, BigDecimal fee, BigDecimal cashAfter, BigDecimal sharesAfter) {}
  public record State(Observation observation, BigDecimal cash, BigDecimal shares, List<Event> events, boolean complete) {
    public State { events = List.copyOf(events); }
  }

  private final List<DailyBar> bars;
  private final BigDecimal feeRate;
  private int cursor;
  private BigDecimal cash;
  private BigDecimal shares = BigDecimal.ZERO;
  private final List<Event> events = new ArrayList<>();

  public WalkForwardReplay(List<DailyBar> bars, BigDecimal startingCash, BigDecimal feeRate) {
    Objects.requireNonNull(bars, "bars");
    if (bars.isEmpty()) throw new IllegalArgumentException("至少需要一筆日收盤資料。");
    this.bars = List.copyOf(bars);
    for (int i = 0; i < this.bars.size(); i++) {
      DailyBar bar = Objects.requireNonNull(this.bars.get(i), "bar");
      if (bar.date() == null || bar.close() == null || bar.close().signum() <= 0) throw new IllegalArgumentException("日期及收盤價必須有效，收盤價須大於零。");
      if (i > 0 && !this.bars.get(i - 1).date().isBefore(bar.date())) throw new IllegalArgumentException("日資料必須依日期嚴格遞增且不可重複。");
    }
    if (startingCash == null || startingCash.signum() < 0) throw new IllegalArgumentException("起始現金不可為負數。");
    if (feeRate == null || feeRate.signum() < 0 || feeRate.compareTo(BigDecimal.ONE) >= 0) throw new IllegalArgumentException("費率必須介於 0（含）與 1（不含）之間。");
    this.cash = startingCash;
    this.feeRate = feeRate;
  }

  /** Only the present cursor is observable. No method exposes the private source list. */
  public synchronized Observation observe() {
    return cursor < bars.size() ? new Observation(bars.get(cursor).date(), bars.get(cursor).close()) : null;
  }

  /** A decision is mandatory for the current observation; each accepted decision advances exactly one bar. */
  public synchronized State decide(Decision decision) {
    Objects.requireNonNull(decision, "decision");
    if (cursor >= bars.size()) throw new IllegalStateException("練習已完成。");
    DailyBar bar = bars.get(cursor);
    BigDecimal gross = bar.close().multiply(decision.quantity());
    BigDecimal fee = gross.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    switch (decision.action()) {
      case WAIT -> { /* Explicitly record a no-op decision. */ }
      case BUY -> {
        BigDecimal debit = gross.add(fee);
        if (debit.compareTo(cash) > 0) throw new IllegalArgumentException("現金不足，買入未執行且游標未前進。");
        cash = cash.subtract(debit);
        shares = shares.add(decision.quantity());
      }
      case SELL -> {
        if (decision.quantity().compareTo(shares) > 0) throw new IllegalArgumentException("持有股數不足，賣出未執行且游標未前進。");
        cash = cash.add(gross.subtract(fee));
        shares = shares.subtract(decision.quantity());
      }
    }
    events.add(new Event(bar.date(), bar.close(), decision.action(), decision.quantity(), gross, fee, cash, shares));
    cursor++;
    return state();
  }

  public synchronized State state() {
    Observation present = observe();
    return new State(present, cash, shares, events, cursor >= bars.size());
  }

  /** Rebuilds state from a fresh exercise and the exact ordered user decisions. */
  public static State reconstruct(List<DailyBar> privateBars, BigDecimal startingCash, BigDecimal feeRate, List<Decision> decisions) {
    Objects.requireNonNull(decisions, "decisions");
    WalkForwardReplay replay = new WalkForwardReplay(privateBars, startingCash, feeRate);
    for (Decision decision : decisions) replay.decide(decision);
    return replay.state();
  }
}
