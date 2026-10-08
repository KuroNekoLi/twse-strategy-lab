package uk.kuronekoli.strategylab.replay;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import uk.kuronekoli.strategylab.market.DailyBar;

/** MVC-level contract checks; all fixture prices are synthetic and no network is used. */
class ReplaySessionHttpContractTest {
  private MutableClock clock;
  private ReplaySessionService service;
  private MockMvc mvc;
  private JsonMapper json;

  @BeforeEach void setUp() {
    clock = new MutableClock(Instant.parse("2030-01-01T00:00:00Z"));
    service = new ReplaySessionService(clock, fixtureBars());
    mvc = MockMvcBuilders.standaloneSetup(new ReplaySessionController(service))
        .setControllerAdvice(new ReplaySessionExceptionHandler(), new uk.kuronekoli.strategylab.api.ApiExceptionHandler())
        .build();
    json = JsonMapper.builder().build();
  }

  @Test void futureBarsRemainPrivateUntilAValidDecisionAdvances() throws Exception {
    JsonNode start = create();
    String id = start.path("sessionId").asText();
    assertEquals("2030-02-01", start.path("observation").path("date").asText());
    assertFalse(start.toString().contains("2030-02-02"));
    assertFalse(start.toString().contains("999999.99"));

    mvc.perform(post(path(id) + "/decisions").contentType("application/json").content("{\"action\":\"BUY\",\"shares\":99999}"))
        .andExpect(status().isBadRequest());
    JsonNode unchanged = get(id);
    assertEquals("2030-02-01", unchanged.path("observation").path("date").asText());
    assertEquals(0, unchanged.path("events").size());

    JsonNode advanced = decide(id, "WAIT", null);
    assertEquals("2030-02-02", advanced.path("observation").path("date").asText());
    assertFalse(advanced.toString().contains("2030-02-03"));
    assertFalse(advanced.toString().contains("999999.99"));
    assertEquals("2030-02-01", advanced.path("events").get(0).path("date").asText());
  }

  @Test void invalidCashAndSharesDoNotAdvanceCursor() throws Exception {
    String id = create().path("sessionId").asText();
    mvc.perform(post(path(id) + "/decisions").contentType("application/json").content("{\"action\":\"BUY\",\"shares\":1001}"))
        .andExpect(status().isBadRequest());
    assertEquals("2030-02-01", get(id).path("observation").path("date").asText());
    assertEquals(0, get(id).path("events").size());

    decide(id, "BUY", 2L);
    mvc.perform(post(path(id) + "/decisions").contentType("application/json").content("{\"action\":\"SELL\",\"shares\":3}"))
        .andExpect(status().isBadRequest());
    JsonNode unchanged = get(id);
    assertEquals("2030-02-02", unchanged.path("observation").path("date").asText());
    assertEquals(1, unchanged.path("events").size());
  }

  @Test void sameFixtureAndDecisionsProduceSamePublicStates() throws Exception {
    String one = create().path("sessionId").asText();
    String two = create().path("sessionId").asText();
    JsonNode a = decide(one, "BUY", 3L);
    JsonNode b = decide(two, "BUY", 3L);
    assertEquals(a.path("observation"), b.path("observation"));
    assertEquals(a.path("state"), b.path("state"));
    assertEquals(a.path("events"), b.path("events"));
  }

  @Test void rejectsUnknownChallengeAndUnknownOrExpiredSessions() throws Exception {
    mvc.perform(post("/api/replay/sessions").contentType("application/json").content("{\"challengeId\":\"real-history\"}"))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("UNKNOWN_CHALLENGE"));
    String id = create().path("sessionId").asText();
    clock.advanceSeconds(1801);
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(id))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REPLAY_SESSION_NOT_FOUND"));
  }

  @Test void capsInMemorySessionsAtSixtyFour() throws Exception {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < ReplaySessionService.MAX_SESSIONS + 1; i++) ids.add(create().path("sessionId").asText());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(ids.get(0)))).andExpect(status().isNotFound());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(ids.get(ids.size() - 1)))).andExpect(status().isOk());
  }

  private JsonNode create() throws Exception {
    String body = mvc.perform(post("/api/replay/sessions").contentType("application/json").content("{}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.challenge.label").value(org.hamcrest.Matchers.containsString("合成")))
        .andReturn().getResponse().getContentAsString();
    return json.readTree(body);
  }
  private JsonNode get(String id) throws Exception {
    return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(id))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  }
  private JsonNode decide(String id, String action, Long shares) throws Exception {
    String content = "{\"action\":\"" + action + "\"" + (shares == null ? "" : ",\"shares\":" + shares) + "}";
    String body = mvc.perform(post(path(id) + "/decisions").contentType("application/json").content(content))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body);
  }
  private static String path(String id) { return "/api/replay/sessions/" + id; }
  private static List<DailyBar> fixtureBars() {
    return List.of(
        new DailyBar(LocalDate.parse("2030-02-01"), new BigDecimal("100.00")),
        new DailyBar(LocalDate.parse("2030-02-02"), new BigDecimal("101.00")),
        new DailyBar(LocalDate.parse("2030-02-03"), new BigDecimal("999999.99")));
  }

  static final class MutableClock extends Clock {
    private Instant instant;
    MutableClock(Instant instant) { this.instant = instant; }
    void advanceSeconds(long seconds) { instant = instant.plusSeconds(seconds); }
    @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    @Override public Instant instant() { return instant; }
  }
}
