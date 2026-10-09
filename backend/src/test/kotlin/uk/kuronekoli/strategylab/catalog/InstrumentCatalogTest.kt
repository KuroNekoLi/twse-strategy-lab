package uk.kuronekoli.strategylab.catalog

import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/** Synthetic responses and injected transport only; no source GET is executed. */
class InstrumentCatalogTest {
    private val company = URI.create("https://example.invalid/company")
    private val fund = URI.create("https://example.invalid/fund")
    private val tpex = URI.create("https://example.invalid/tpex")
    private val mapper = JsonMapper.builder().build()
    class MutableClock : Clock() {
        var now = Instant.parse("2026-10-09T00:00:00Z")
        fun advance(seconds: Long) { now = now.plusSeconds(seconds) }
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
    }
    private fun client(clock: Clock, transport: InstrumentCatalogClient.Transport) = InstrumentCatalogClient(mapper, company, fund, tpex, Duration.ofSeconds(60), clock) { uri -> if (uri == tpex) InstrumentCatalogClient.TransportResponse(200, TPEX_COMPANIES) else transport.get(uri) }
    private fun validClient() = client(MutableClock()) { uri -> InstrumentCatalogClient.TransportResponse(200, if (uri == company) COMPANIES else FUNDS) }

    @Test fun whitelistedSearchReturnsDeterministicCodeNameKindAndParsedAsOf() {
        val service = InstrumentCatalogService(validClient())
        val response = service.search("台", 20)
        assertEquals(listOf("1240", "1301", "2308", "2330", "0050"), response.items.map { it.code })
        assertEquals("STOCK", response.items[0].kind); assertEquals("FUND", response.items[4].kind)
        assertEquals("2026-10-08", response.items[3].asOf); assertEquals("2026-10-07", response.items[4].asOf)
        assertEquals(response, service.search("台", 20))
        val json = mapper.writeValueAsString(response)
        listOf("PRIVATE_PERSON", "PRIVATE_PHONE", "PRIVATE_MANAGER", "董事長", "電話", "經理人").forEach { assertFalse(json.contains(it), it) }
        assertEquals(3, response.sources.size); assertEquals("https://data.gov.tw/dataset/18419", response.sources[0].datasetUrl)
        assertTrue(response.sources[0].license.contains("OGDL v1.0")); assertEquals("MONTHLY", response.sources[0].updateFrequency)
        assertEquals("2026-10-07", response.sources[1].asOfFrom); assertEquals("2026-10-08", response.sources[1].asOfTo)
        val otc = response.items.single { it.code == "1240" }
        assertEquals("TPEX", otc.market); assertFalse(otc.backtestSupported)
        assertEquals("2026-10-09T00:00:00Z", response.sources[0].fetchedAt)
    }
    @Test fun fullWidthQueriesSuffixCodesLimitsAndNoMatchesAreHandled() {
        val service = InstrumentCatalogService(validClient())
        val exact = service.search(" ２３３０ ", 1); assertEquals("2330", exact.query); assertEquals("2330", exact.items[0].code)
        assertEquals("00679B", service.search("00679b", 20).items[0].code)
        val limited = service.search("電", 1); assertEquals(3, limited.totalMatches); assertEquals(1, limited.items.size)
        assertEquals("2303", limited.items[0].code); assertTrue(service.search("沒有符合名稱", 20).items.isEmpty())
    }
    @Test fun invalidQueryAndLimitFailBeforeAnySourceCall() {
        val calls = AtomicInteger()
        val service = InstrumentCatalogService(client(MutableClock()) { calls.incrementAndGet(); throw IllegalStateException("unreachable") })
        listOf<String?>(null, "", "  ", "x".repeat(61), "ﬃ".repeat(60), "test\n", "\u0000").forEach { query ->
            assertEquals("INVALID_CATALOG_QUERY", assertThrows(CatalogException::class.java) { service.search(query, 20) }.code)
        }
        listOf(0, -1, 51, Int.MAX_VALUE).forEach { limit -> assertEquals(400, assertThrows(CatalogException::class.java) { service.search("台", limit) }.status) }
        assertEquals(0, calls.get())
    }
    @Test fun perResourceTtlReusesOnlyNormalizedSnapshotsAndRefreshesAtExpiry() {
        val clock = MutableClock(); val companyCalls = AtomicInteger(); val fundCalls = AtomicInteger(); val companies = AtomicReference(COMPANIES)
        val upstream = client(clock) { uri -> if (uri == company) { companyCalls.incrementAndGet(); InstrumentCatalogClient.TransportResponse(200, companies.get()) } else { fundCalls.incrementAndGet(); InstrumentCatalogClient.TransportResponse(200, FUNDS) } }
        val service = InstrumentCatalogService(upstream)
        service.search("台", 20); clock.advance(59); service.search("00679B", 20)
        assertEquals(1, companyCalls.get()); assertEquals(1, fundCalls.get())
        companies.set(COMPANIES.replace("台積電", "測試新名稱").replace("1151008", "1151009"))
        clock.advance(1); service.refresh(); val refreshed = service.search("2330", 20)
        assertEquals(2, companyCalls.get()); assertEquals(2, fundCalls.get()); assertEquals("測試新名稱", refreshed.items[0].name)
        assertEquals("2026-10-09", refreshed.items[0].asOf); assertEquals("2026-10-09T00:01:00Z", refreshed.sources[0].fetchedAt)
    }
    @Test fun failedRefreshKeepsTheLastPersistedCatalogSearchable() {
        val clock = MutableClock(); val calls = AtomicInteger(); val status = AtomicReference(200)
        val upstream = client(clock) { uri -> if (uri == company) { calls.incrementAndGet(); InstrumentCatalogClient.TransportResponse(status.get(), COMPANIES) } else InstrumentCatalogClient.TransportResponse(200, FUNDS) }
        val service = InstrumentCatalogService(upstream)
        service.search("2330", 20); clock.advance(60); status.set(503)
        val error = assertThrows(CatalogException::class.java) { upstream.snapshots() }
        assertEquals("CATALOG_UPSTREAM_UNAVAILABLE", error.code); assertEquals(1, service.search("2330", 20).items.size); assertEquals(2, calls.get())
    }
    @Test fun malformedEmptyDuplicateMissingOrInvalidRecordsFailWholeResource() {
        val client = validClient()
        listOf("{invalid", "{}", "null", "[null]", COMPANIES + "[]", COMPANIES.replace("\"公司代號\":\"2330\"", "\"公司代號\":\"2330\",\"公司代號\":\"2300\""), "[{\"公司代號\":\"2330\",\"公司名稱\":\"台積電\"}]", COMPANIES.replace("1151008", "1150230"), COMPANIES.replace("1151008", "unknown"), COMPANIES.replace("2330", "bad"), COMPANIES.replace("2303", "2330"), COMPANIES.replace("台積電", "").replace("台灣積體電路製造股份有限公司", ""), COMPANIES.replace("\"2330\"", "2330")).forEach { body ->
            val error = assertThrows(CatalogException::class.java) { client.parse(InstrumentCatalogClient.Resource.COMPANY, body) }
            assertEquals("CATALOG_SCHEMA_INVALID", error.code); assertEquals(502, error.status); assertFalse(error.message!!.contains("PRIVATE_"))
        }
        assertEquals("CATALOG_DATA_EMPTY", assertThrows(CatalogException::class.java) { client.parse(InstrumentCatalogClient.Resource.COMPANY, "[]") }.code)
    }
    @Test fun oneResourceFailurePreventsPartialSearchAndCodeConflictFailsClosed() {
        val failure = InstrumentCatalogService(client(MutableClock()) { uri -> InstrumentCatalogClient.TransportResponse(200, if (uri == company) COMPANIES else "[]") })
        assertEquals("CATALOG_DATA_EMPTY", assertThrows(CatalogException::class.java) { failure.search("2330", 20) }.code)
        val conflict = InstrumentCatalogService(client(MutableClock()) { uri -> InstrumentCatalogClient.TransportResponse(200, if (uri == company) COMPANIES else FUNDS.replace("0050", "2330")) })
        assertEquals("CATALOG_CODE_CONFLICT", assertThrows(CatalogException::class.java) { conflict.search("2330", 20) }.code)
    }
    @Test fun transportFailureAndNonSuccessStatusAreSanitized() {
        val failure = InstrumentCatalogService(client(MutableClock()) { throw IllegalStateException("SECRET_PROVIDER_BODY") })
        val error = assertThrows(CatalogException::class.java) { failure.search("2330", 20) }
        assertEquals("CATALOG_UPSTREAM_UNAVAILABLE", error.code); assertFalse(error.message!!.contains("SECRET"))
        val status = InstrumentCatalogService(client(MutableClock()) { InstrumentCatalogClient.TransportResponse(302, COMPANIES) })
        assertEquals(502, assertThrows(CatalogException::class.java) { status.search("2330", 20) }.status)
    }
    companion object {
        const val COMPANIES = """[{"公司代號":"2330","公司名稱":"台灣積體電路製造股份有限公司","公司簡稱":"台積電","出表日期":"1151008","董事長":"PRIVATE_PERSON","電話":"PRIVATE_PHONE"},{"公司代號":"2303","公司名稱":"聯電","出表日期":"20261008"},{"公司代號":"2308","公司名稱":"台達電子工業股份有限公司","公司簡稱":"台達電","出表日期":"1151008"},{"公司代號":"1301","公司名稱":"台灣塑膠工業股份有限公司","公司簡稱":"台塑","出表日期":"1151008"}]"""
        const val FUNDS = """[{"基金代號":"0050","基金簡稱":"測試台灣50","出表日期":"2026-10-07","經理人":"PRIVATE_MANAGER"},{"基金代號":"00679B","基金簡稱":"測試債券","出表日期":"115/10/08"}]"""
        const val TPEX_COMPANIES = """[{"Date":"1151008","SecuritiesCompanyCode":"1240","CompanyName":"台灣測試股份有限公司","CompanyAbbreviation":"台灣測試","Chairman":"PRIVATE_PERSON"}]"""
    }
}
