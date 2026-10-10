package uk.kuronekoli.strategylab.market

import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository

interface DailyMarketBarRepository : JpaRepository<DailyMarketBarEntity, Long> {
    fun findAllBySymbolAndAdjustmentPolicyAndTradingDateBetweenOrderByTradingDateAsc(symbol: String, adjustmentPolicy: String, from: LocalDate, to: LocalDate): List<DailyMarketBarEntity>
    fun findAllByTradingDateAndAdjustmentPolicy(date: LocalDate, adjustmentPolicy: String): List<DailyMarketBarEntity>
    fun findFirstBySymbolOrderByTradingDateAsc(symbol: String): DailyMarketBarEntity?
    fun findFirstBySymbolOrderByTradingDateDesc(symbol: String): DailyMarketBarEntity?
    fun findFirstBySymbolAndAdjustmentPolicyOrderByTradingDateAsc(symbol: String, adjustmentPolicy: String): DailyMarketBarEntity?
    fun findTopByOrderByLastVerifiedAtDesc(): DailyMarketBarEntity?
}

interface MarketDataSourceRepository : JpaRepository<MarketDataSourceEntity, String>

interface MarketDataIngestionRunRepository : JpaRepository<MarketDataIngestionRunEntity, String> {
    fun findTopByOrderByStartedAtDesc(): MarketDataIngestionRunEntity?
}
