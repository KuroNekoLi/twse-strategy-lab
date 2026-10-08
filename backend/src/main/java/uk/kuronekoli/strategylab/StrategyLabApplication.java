package uk.kuronekoli.strategylab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StrategyLabApplication {
  public static void main(String[] args) { SpringApplication.run(StrategyLabApplication.class, args); }
}
