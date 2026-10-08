package uk.kuronekoli.strategylab.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/** Immutable user-command/fill facts; prices are supplied inputs, never fetched here. */
public record PaperAccountEvent(
    String eventId,
    LocalDate effectiveDate,
    Type type,
    String symbol,
    BigDecimal amount,
    long quantity,
    BigDecimal price,
    BigDecimal commissionRate,
    BigDecimal sellTaxRate) {

  public enum Type { INITIAL_DEPOSIT, BUY_FILL, SELL_FILL }

  public PaperAccountEvent {
    if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId is required");
    Objects.requireNonNull(effectiveDate, "effectiveDate is required");
    Objects.requireNonNull(type, "type is required");
    if (type == Type.INITIAL_DEPOSIT) {
      if (amount == null || amount.setScale(2, RoundingMode.HALF_UP).signum() <= 0) throw new IllegalArgumentException("deposit must be at least one cent");
      if (symbol != null || quantity != 0 || price != null || commissionRate != null || sellTaxRate != null)
        throw new IllegalArgumentException("deposit cannot contain fill fields");
    } else {
      if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
      if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
      if (price == null || price.setScale(2, RoundingMode.HALF_UP).signum() <= 0) throw new IllegalArgumentException("price must be at least one cent");
      rate(commissionRate, "commissionRate");
      rate(sellTaxRate, "sellTaxRate");
      if (type == Type.SELL_FILL && commissionRate.add(sellTaxRate).compareTo(BigDecimal.ONE) > 0)
        throw new IllegalArgumentException("combined sell charges cannot exceed gross value");
      if (amount != null) throw new IllegalArgumentException("fill cannot contain deposit amount");
      if (type == Type.BUY_FILL && sellTaxRate.signum() != 0)
        throw new IllegalArgumentException("buy fill cannot contain sell tax");
    }
  }

  public static PaperAccountEvent initialDeposit(String id, LocalDate date, BigDecimal amount) {
    return new PaperAccountEvent(id, date, Type.INITIAL_DEPOSIT, null, amount, 0, null, null, null);
  }

  public static PaperAccountEvent buy(String id, LocalDate date, String symbol, long quantity,
      BigDecimal price, BigDecimal commissionRate) {
    return new PaperAccountEvent(id, date, Type.BUY_FILL, symbol, null, quantity, price,
        commissionRate, BigDecimal.ZERO);
  }

  public static PaperAccountEvent sell(String id, LocalDate date, String symbol, long quantity,
      BigDecimal price, BigDecimal commissionRate, BigDecimal sellTaxRate) {
    return new PaperAccountEvent(id, date, Type.SELL_FILL, symbol, null, quantity, price,
        commissionRate, sellTaxRate);
  }

  private static void rate(BigDecimal value, String field) {
    if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.ONE) > 0)
      throw new IllegalArgumentException(field + " must be between 0 and 1");
  }
}
