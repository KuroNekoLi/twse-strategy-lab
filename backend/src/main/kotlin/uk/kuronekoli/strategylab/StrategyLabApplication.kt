package uk.kuronekoli.strategylab

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class StrategyLabApplication

fun main(args: Array<String>) {
  SpringApplication.run(StrategyLabApplication::class.java, *args)
}
