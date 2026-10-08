package uk.kuronekoli.strategylab.market;

import java.time.LocalDate;

public record DailyBar(LocalDate date, double close) {}
