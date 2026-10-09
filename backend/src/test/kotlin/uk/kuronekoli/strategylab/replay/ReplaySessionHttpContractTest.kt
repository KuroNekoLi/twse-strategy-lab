package uk.kuronekoli.strategylab.replay

import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import uk.kuronekoli.strategylab.market.DailyBar
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/** MVC contract checks with synthetic fixtures; no market network is used. */
class ReplaySessionHttpContractTest {
    private lateinit var clock: MutableClock; private lateinit var mvc: MockMvc; private lateinit var json: JsonMapper
    @BeforeEach fun setUp() { clock=MutableClock(Instant.parse("2030-01-01T00:00:00Z")); val service=ReplaySessionService(clock,fixtureBars()); mvc=MockMvcBuilders.standaloneSetup(ReplaySessionController(service)).setControllerAdvice(ReplaySessionExceptionHandler(),uk.kuronekoli.strategylab.api.ApiExceptionHandler()).build(); json=JsonMapper.builder().build() }
    @Test fun futureBarsArePrivateUntilDecisionAdvances() { val start=create(); val id=start.path("sessionId").asText(); assertEquals("2030-02-01",start.path("observation").path("date").asText()); assertFalse(start.toString().contains("2030-02-02")); assertFalse(start.toString().contains("999999.99")); mvc.perform(post(path(id)+"/decisions").contentType("application/json").content("{\"action\":\"BUY\",\"shares\":99999}")).andExpect(status().isBadRequest()); assertEquals("2030-02-01",get(id).path("observation").path("date").asText()); assertEquals(0,get(id).path("events").size()); val advanced=decide(id,"WAIT",null); assertEquals("2030-02-02",advanced.path("observation").path("date").asText()); assertFalse(advanced.toString().contains("2030-02-03")); assertFalse(advanced.toString().contains("999999.99")); assertEquals("2030-02-01",advanced.path("events").get(0).path("date").asText()) }
    @Test fun invalidCashAndSharesDoNotAdvance() { val id=create().path("sessionId").asText(); mvc.perform(post(path(id)+"/decisions").contentType("application/json").content("{\"action\":\"BUY\",\"shares\":1001}")).andExpect(status().isBadRequest()); assertEquals("2030-02-01",get(id).path("observation").path("date").asText()); decide(id,"BUY",2); mvc.perform(post(path(id)+"/decisions").contentType("application/json").content("{\"action\":\"SELL\",\"shares\":3}")).andExpect(status().isBadRequest()); assertEquals("2030-02-02",get(id).path("observation").path("date").asText()); assertEquals(1,get(id).path("events").size()) }
    @Test fun rejectsUnknownChallengeAndExpiredSession() { mvc.perform(post("/api/replay/sessions").contentType("application/json").content("{\"challengeId\":\"real-history\"}")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("UNKNOWN_CHALLENGE")); val id=create().path("sessionId").asText(); clock.advanceSeconds(1801); mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(id))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLAY_SESSION_NOT_FOUND")) }
    @Test fun capsStoredSessionsAtSixtyFour() { val ids=(0..ReplaySessionService.MAX_SESSIONS).map{create().path("sessionId").asText()}; mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(ids.first()))).andExpect(status().isNotFound()); mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(ids.last()))).andExpect(status().isOk()) }
    private fun create(): JsonNode { val body=mvc.perform(post("/api/replay/sessions").contentType("application/json").content("{}")).andExpect(status().isOk()).andReturn().response.contentAsString; return json.readTree(body) }
    private fun get(id:String):JsonNode=json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(id))).andExpect(status().isOk()).andReturn().response.contentAsString)
    private fun decide(id:String,action:String,shares:Long?):JsonNode { val content="{\"action\":\"$action\""+(shares?.let{",\"shares\":$it"} ?: "")+"}"; return json.readTree(mvc.perform(post(path(id)+"/decisions").contentType("application/json").content(content)).andExpect(status().isOk()).andReturn().response.contentAsString) }
    private fun path(id:String)="/api/replay/sessions/$id"
    private fun fixtureBars()=listOf(DailyBar(LocalDate.parse("2030-02-01"),BigDecimal("100.00")),DailyBar(LocalDate.parse("2030-02-02"),BigDecimal("101.00")),DailyBar(LocalDate.parse("2030-02-03"),BigDecimal("999999.99")))
    class MutableClock(private var current:Instant):Clock() { fun advanceSeconds(seconds:Long){current=current.plusSeconds(seconds)}; override fun getZone():ZoneId=ZoneOffset.UTC; override fun withZone(zone:ZoneId):Clock=this; override fun instant():Instant=current }
}
