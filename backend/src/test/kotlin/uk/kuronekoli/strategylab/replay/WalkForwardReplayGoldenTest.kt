package uk.kuronekoli.strategylab.replay

import java.math.BigDecimal
import java.time.LocalDate
import uk.kuronekoli.strategylab.market.DailyBar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WalkForwardReplayGoldenTest {
    private fun d(s:String)=BigDecimal(s)
    private fun fixture()=listOf(DailyBar(LocalDate.parse("2025-01-02"),d("10.00")),DailyBar(LocalDate.parse("2025-01-03"),d("100.00")),DailyBar(LocalDate.parse("2025-01-06"),d("20.00")))
    @Test fun revealsOneCloseAndRequiresDecisionToAdvance() { val replay=WalkForwardReplay(fixture(),d("100"),d("0")); assertEquals(WalkForwardReplay.Observation(LocalDate.parse("2025-01-02"),d("10.00")),replay.observe()); val next=replay.decide(WalkForwardReplay.Decision(WalkForwardReplay.Action.WAIT,d("0"))); assertEquals(LocalDate.parse("2025-01-03"),next.observation!!.date); assertEquals(d("100"),next.cash); assertEquals(1,next.events.size); assertThrows(UnsupportedOperationException::class.java){(next.events as MutableList).clear()} }
    @Test fun exactAccountingAndReconstructionAreDeterministic() { val decisions=listOf(WalkForwardReplay.Decision(WalkForwardReplay.Action.BUY,d("5")),WalkForwardReplay.Decision(WalkForwardReplay.Action.SELL,d("2")),WalkForwardReplay.Decision(WalkForwardReplay.Action.WAIT,d("0"))); val expected=WalkForwardReplay.reconstruct(fixture(),d("100"),d("0.01"),decisions); assertEquals(expected,WalkForwardReplay.reconstruct(fixture(),d("100"),d("0.01"),decisions)); assertTrue(expected.complete); assertNull(expected.observation); assertEquals(d("0.50"),expected.events[0].fee); assertEquals(d("49.50"),expected.events[0].cashAfter); assertEquals(d("200.00"),expected.events[1].gross); assertEquals(d("2.00"),expected.events[1].fee); assertEquals(d("247.50"),expected.cash); assertEquals(d("3"),expected.shares) }
    @Test fun rejectedDecisionDoesNotAdvance() { val replay=WalkForwardReplay(fixture(),d("50"),d("0.01")); assertThrows(IllegalArgumentException::class.java){replay.decide(WalkForwardReplay.Decision(WalkForwardReplay.Action.BUY,d("6")))}; assertEquals(LocalDate.parse("2025-01-02"),replay.observe()!!.date); assertTrue(replay.state().events.isEmpty()); assertThrows(IllegalArgumentException::class.java){replay.decide(WalkForwardReplay.Decision(WalkForwardReplay.Action.SELL,d("1")))} }
    @Test fun rejectsInvalidTimelineAndDecisionShapes() { assertThrows(IllegalArgumentException::class.java){WalkForwardReplay(listOf(fixture()[1],fixture()[0]),d("10"),d("0"))}; assertThrows(IllegalArgumentException::class.java){WalkForwardReplay(fixture(),d("10"),d("1"))}; assertThrows(IllegalArgumentException::class.java){WalkForwardReplay.Decision(WalkForwardReplay.Action.WAIT,d("1"))}; assertThrows(IllegalArgumentException::class.java){WalkForwardReplay.Decision(WalkForwardReplay.Action.BUY,d("0"))} }
}
