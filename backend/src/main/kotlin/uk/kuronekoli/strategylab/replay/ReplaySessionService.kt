package uk.kuronekoli.strategylab.replay

import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedHashMap
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.kuronekoli.strategylab.market.DailyBar
import uk.kuronekoli.strategylab.replay.ReplaySessionStore.DecisionRecord
import uk.kuronekoli.strategylab.replay.ReplaySessionStore.SessionRecord
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Action
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Decision
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.Event
import uk.kuronekoli.strategylab.replay.WalkForwardReplay.State

@Service
class ReplaySessionService {
    companion object {
        const val CHALLENGE_ID = "synthetic-intro-v1"
        const val MAX_SESSIONS = 64
        private val SESSION_TTL = Duration.ofMinutes(30)
        private val STARTING_CASH = BigDecimal("100000.00")
        private val FEE_RATE = BigDecimal("0.001425")
        private val CHALLENGE = ReplayChallengeResponse.Challenge(CHALLENGE_ID,"合成價格觀察練習","完全合成的教學資料；不是任何真實證券或市場行情","價格與日期均為固定合成樣本，不含股利、公司行動或真實交易日曆；不構成投資建議。每次決策後才會顯示下一個觀察點。匿名練習最多保留 30 分鐘。")
        private fun defaultBars() = listOf(DailyBar(LocalDate.of(2031,1,6),BigDecimal("100.00")),DailyBar(LocalDate.of(2031,1,7),BigDecimal("96.00")),DailyBar(LocalDate.of(2031,1,8),BigDecimal("103.00")),DailyBar(LocalDate.of(2031,1,9),BigDecimal("101.00")),DailyBar(LocalDate.of(2031,1,12),BigDecimal("109.00")))
    }
    private val store: ReplaySessionStore
    private val clock: Clock
    private val challengeBars: List<DailyBar>
    @Autowired constructor(store: ReplaySessionStore): this(store,Clock.systemUTC(),defaultBars())
    constructor(clock: Clock, challengeBars: List<DailyBar>): this(MemoryStore(),clock,challengeBars)
    constructor(store: ReplaySessionStore, clock: Clock, challengeBars: List<DailyBar>) { this.store=store; this.clock=clock; this.challengeBars=challengeBars.toList() }
    @Transactional @Synchronized fun create(challengeId: String?): ReplayChallengeResponse {
        purgeExpired(); if (challengeId != null && challengeId != CHALLENGE_ID) throw ReplaySessionException(HttpStatus.NOT_FOUND,"UNKNOWN_CHALLENGE","找不到此教學練習。")
        while (store.oldestFirst().size >= MAX_SESSIONS) store.delete(store.oldestFirst()[0].id)
        val now=clock.instant(); val id=UUID.randomUUID().toString(); store.save(SessionRecord(id,CHALLENGE_ID,now,now,now.plus(SESSION_TTL)))
        return response(id,replayFor(id))
    }
    @Transactional @Synchronized fun get(id: String): ReplayChallengeResponse { val session=required(id); val now=clock.instant(); store.save(SessionRecord(session.id,session.challengeId,session.createdAt,now,now.plus(SESSION_TTL))); return response(id,replayFor(id)) }
    @Transactional @Synchronized fun decide(id: String, request: ReplayDecisionRequest?): ReplayChallengeResponse {
        val session=required(id); require(request?.action != null) { "請提供 WAIT、BUY 或 SELL 決策。" }; val action=request.action!!
        val quantity=request.quantity(); val replay=replayFor(id); replay.decide(Decision(action,quantity)); val now=clock.instant(); val existing=store.decisions(id)
        val shares=if(action==Action.WAIT) null else request.shares
        store.appendDecision(id,DecisionRecord(existing.size,action,shares,now)); store.save(SessionRecord(session.id,session.challengeId,session.createdAt,now,now.plus(SESSION_TTL)))
        return response(id,replayFor(id))
    }
    private fun required(id: String): SessionRecord { purgeExpired(); val session=store.find(id).orElse(null); if(session==null || !session.expiresAt.isAfter(clock.instant())) { if(session!=null) store.delete(id); throw ReplaySessionException(HttpStatus.NOT_FOUND,"REPLAY_SESSION_NOT_FOUND","練習不存在或已逾期，請重新開始。") }; return session }
    private fun purgeExpired() { store.findExpired(clock.instant()).forEach { store.delete(it.id) } }
    private fun replayFor(id: String): WalkForwardReplay { val decisions=store.decisions(id).map { Decision(it.action,if(it.action==Action.WAIT) BigDecimal.ZERO else BigDecimal.valueOf(it.shares!!)) }; return WalkForwardReplay(challengeBars,STARTING_CASH,FEE_RATE).also { replay -> decisions.forEach(replay::decide) } }
    private fun response(id: String,replay: WalkForwardReplay): ReplayChallengeResponse { val state=replay.state(); val observation=state.observation?.let { ReplayChallengeResponse.Observation(it.date,it.close) }; val events=state.events.map(::event); return ReplayChallengeResponse(id,CHALLENGE,observation,ReplayChallengeResponse.Portfolio(state.cash,state.shares,state.events.size,state.complete),events) }
    private fun event(event: Event)=ReplayChallengeResponse.DecisionEvent(event.date,event.observedClose,event.action.name,event.quantity,event.cashAfter,event.sharesAfter)
    private class MemoryStore: ReplaySessionStore {
        private val sessions=LinkedHashMap<String,SessionRecord>(16,0.75f,true); private val decisions=linkedMapOf<String,MutableList<DecisionRecord>>()
        override fun save(session: SessionRecord): SessionRecord { sessions[session.id]=session; return session }
        override fun find(id: String)=java.util.Optional.ofNullable(sessions[id])
        override fun findExpired(now: Instant): List<SessionRecord> { val expired=sessions.values.filter{!it.expiresAt.isAfter(now)}; expired.forEach{delete(it.id)}; return expired }
        override fun oldestFirst()=sessions.values.toList()
        override fun decisions(sessionId: String)=decisions[sessionId]?.toList() ?: emptyList()
        override fun appendDecision(sessionId: String, decision: DecisionRecord) { decisions.getOrPut(sessionId){mutableListOf()}.add(decision) }
        override fun delete(sessionId: String) { sessions.remove(sessionId); decisions.remove(sessionId) }
    }
}
