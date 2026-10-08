package uk.kuronekoli.strategylab.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Rebuildable account state derived solely by applying the ordered event ledger. */
public final class PaperAccountProjection {
  private static final BigDecimal ZERO = new BigDecimal("0.00");
  private BigDecimal cash = ZERO;
  private final Map<String, Position> positions = new LinkedHashMap<>();
  private boolean initialized;

  public record Position(long quantity, BigDecimal costBasis) {}

  public BigDecimal cash() { return cash; }
  public Map<String, Position> positions() { return Collections.unmodifiableMap(positions); }
  public Position position(String symbol) { return positions.getOrDefault(symbol, new Position(0, ZERO)); }
  boolean initialized() { return initialized; }

  void apply(PaperAccountEvent event) {
    switch (event.type()) {
      case INITIAL_DEPOSIT -> deposit(event);
      case BUY_FILL -> buy(event);
      case SELL_FILL -> sell(event);
    }
  }

  private void deposit(PaperAccountEvent event) {
    if (initialized) throw new IllegalArgumentException("initial deposit already recorded");
    initialized = true;
    cash = cash.add(money(event.amount()));
  }

  private void buy(PaperAccountEvent event) {
    requireInitialized();
    Position current = position(event.symbol());
    if (Long.MAX_VALUE - current.quantity() < event.quantity()) throw new IllegalArgumentException("position quantity overflow");
    BigDecimal gross = money(event.price().multiply(BigDecimal.valueOf(event.quantity())));
    BigDecimal fee = money(gross.multiply(event.commissionRate()));
    BigDecimal total = gross.add(fee);
    if (cash.compareTo(total) < 0) throw new IllegalArgumentException("insufficient cash");
    cash = cash.subtract(total);
    positions.put(event.symbol(), new Position(current.quantity() + event.quantity(), current.costBasis().add(total)));
  }

  private void sell(PaperAccountEvent event) {
    requireInitialized();
    Position current = position(event.symbol());
    if (current.quantity() < event.quantity()) throw new IllegalArgumentException("insufficient shares");
    BigDecimal gross = money(event.price().multiply(BigDecimal.valueOf(event.quantity())));
    BigDecimal fee = money(gross.multiply(event.commissionRate()));
    BigDecimal tax = money(gross.multiply(event.sellTaxRate()));
    cash = cash.add(gross.subtract(fee).subtract(tax));
    long remaining = current.quantity() - event.quantity();
    BigDecimal remainingCost = remaining == 0 ? ZERO : current.costBasis()
        .multiply(BigDecimal.valueOf(remaining)).divide(BigDecimal.valueOf(current.quantity()), 2, RoundingMode.HALF_UP);
    if (remaining == 0) positions.remove(event.symbol());
    else positions.put(event.symbol(), new Position(remaining, remainingCost));
  }

  private void requireInitialized() {
    if (!initialized) throw new IllegalArgumentException("initial deposit must be first");
  }

  static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
