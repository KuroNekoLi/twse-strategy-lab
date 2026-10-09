package uk.kuronekoli.strategylab.catalog

import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source

@Component
class InstrumentCatalogClient private constructor() {
    private lateinit var mapper: JsonMapper
    private lateinit var endpoints: Map<Resource, URI>
    private lateinit var ttl: Duration
    private lateinit var clock: Clock
    private lateinit var transport: Transport
    private val cache = ConcurrentHashMap<Resource, Snapshot>()
    private val locks = Resource.entries.associateWith { Any() }

    @Autowired constructor(
        mapper: JsonMapper,
        @Value("\${app.catalog.company-url:https://openapi.twse.com.tw/v1/opendata/t187ap03_L}") companyUrl: String,
        @Value("\${app.catalog.fund-url:https://openapi.twse.com.tw/v1/opendata/t187ap47_L}") fundUrl: String,
        @Value("\${app.catalog.tpex-company-url:https://www.tpex.org.tw/openapi/v1/mopsfin_t187ap03_O}") tpexCompanyUrl: String,
        @Value("\${app.catalog.cache-ttl-seconds:900}") ttlSeconds: Long
    ) : this() { initialize(mapper, companyUrl = URI.create(companyUrl), fundUrl = URI.create(fundUrl), tpexCompanyUrl = URI.create(tpexCompanyUrl), ttl = Duration.ofSeconds(ttlSeconds), clock = Clock.systemUTC(), transport = httpTransport()) }

    internal constructor(mapper: JsonMapper, companyUrl: URI, fundUrl: URI, ttl: Duration, clock: Clock, transport: Transport) : this() { initialize(mapper, companyUrl, fundUrl, URI.create("https://example.invalid/tpex-company"), ttl, clock, transport) }
    internal constructor(mapper: JsonMapper, companyUrl: URI, fundUrl: URI, tpexCompanyUrl: URI, ttl: Duration, clock: Clock, transport: Transport) : this() { initialize(mapper, companyUrl, fundUrl, tpexCompanyUrl, ttl, clock, transport) }

    private fun initialize(mapper: JsonMapper, companyUrl: URI, fundUrl: URI, tpexCompanyUrl: URI, ttl: Duration, clock: Clock, transport: Transport) {
        require(!ttl.isNegative && !ttl.isZero && ttl <= Duration.ofDays(1)) { "Catalog cache TTL must be positive and at most one day" }
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
        this.endpoints = mapOf(Resource.COMPANY to companyUrl, Resource.FUND to fundUrl, Resource.TPEX_COMPANY to tpexCompanyUrl)
        this.ttl = ttl; this.clock = clock; this.transport = transport
    }
    companion object {
        private const val MAX_ROWS = 20000
        private const val MAX_BODY_BYTES = 10 * 1024 * 1024
        private fun httpTransport(): Transport {
            val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
            return Transport { uri ->
                val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").header("User-Agent", "TWSEStrategyLab/1.0 catalog-research").GET().build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
                response.body().use { stream ->
                    if (response.statusCode() !in 200..299) return@Transport TransportResponse(response.statusCode(), "")
                    val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "catalog-body-reader").apply { isDaemon = true } }
                    val bytes = try { reader.submit<ByteArray> { stream.readNBytes(MAX_BODY_BYTES + 1) }.get(20, TimeUnit.SECONDS) } finally { reader.shutdownNow() }
                    if (bytes.size > MAX_BODY_BYTES) throw IllegalStateException("Catalog response too large")
                    TransportResponse(response.statusCode(), String(bytes, StandardCharsets.UTF_8))
                }
            }
        }
    }

    internal enum class Resource(val id: String, val title: String, val dataset: String, val codeField: String, val nameField: String, val kind: String, val market: String, val backtestSupported: Boolean) {
        COMPANY("companies", "上市公司基本資料", "18419", "公司代號", "公司名稱", "STOCK", "TWSE", true),
        FUND("funds", "基金基本資料", "157399", "基金代號", "基金簡稱", "FUND", "TWSE", true),
        TPEX_COMPANY("tpexCompanies", "上櫃公司基本資料", "25036", "SecuritiesCompanyCode", "CompanyAbbreviation", "STOCK", "TPEX", false)
    }
    internal data class TransportResponse(val status: Int, val body: String)
    internal fun interface Transport { @Throws(Exception::class) fun get(uri: URI): TransportResponse }
    data class Snapshot(val rows: List<Instrument>, val source: Source, val expiresAt: Instant)


    internal fun snapshots() = listOf(snapshot(Resource.COMPANY), snapshot(Resource.FUND), snapshot(Resource.TPEX_COMPANY))
    private fun snapshot(resource: Resource): Snapshot = synchronized(locks.getValue(resource)) {
        val now = clock.instant(); val saved = cache[resource]
        if (saved != null && now.isBefore(saved.expiresAt)) return@synchronized saved
        // Expired rows are never returned when refresh fails. A successfully loaded other resource is kept separately.
        cache.remove(resource)
        val endpoint = endpoints.getValue(resource)
        val response = try { transport.get(endpoint) } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
            throw CatalogException.upstream(resource.title)
        }
        if (response.status !in 200..299) throw CatalogException.upstream(resource.title)
        val rows = parse(resource, response.body)
        val fetched = clock.instant()
        val minDate = rows.minOf { it.asOf }; val maxDate = rows.maxOf { it.asOf }
        val source = Source(resource.id, resource.title, if (resource.market == "TPEX") "櫃買中心" else "臺灣證券交易所", "https://data.gov.tw/dataset/${resource.dataset}", endpoint.toString(), "政府資料開放授權條款第1版（OGDL v1.0）", "https://data.gov.tw/license", if (resource.market == "TPEX") "DAILY" else "MONTHLY", fetched.toString(), minDate, maxDate, ttl.seconds)
        Snapshot(rows, source, fetched.plus(ttl)).also { cache[resource] = it }
    }

    internal fun parse(resource: Resource, body: String?): List<Instrument> {
        if (body.isNullOrBlank() || body.length > MAX_BODY_BYTES) throw CatalogException.schema(resource.title)
        val root = try { mapper.readTree(body) } catch (_: RuntimeException) { throw CatalogException.schema(resource.title) }
        if (root == null || !root.isArray) throw CatalogException.schema(resource.title)
        if (root.isEmpty) throw CatalogException("CATALOG_DATA_EMPTY", "${resource.title}內容為空，名錄查詢已停止。", 502)
        if (root.size() > MAX_ROWS) throw CatalogException.schema(resource.title)
        val rows = mutableListOf<Instrument>(); val seen = HashSet<String>()
        for (row in root) {
            if (!row.isObject) throw CatalogException.schema(resource.title)
            val code = text(row, resource.codeField, resource)
            val name = when (resource) {
                Resource.COMPANY -> optionalText(row, "公司簡稱", text(row, resource.nameField, resource))
                Resource.TPEX_COMPANY -> optionalText(row, "CompanyAbbreviation", text(row, "CompanyName", resource))
                Resource.FUND -> text(row, resource.nameField, resource)
            }
            if (!code.matches(Regex("[0-9]{4,6}[A-Z]?")) || name.length > 200 || name.codePoints().anyMatch(Character::isISOControl) || !seen.add(code)) throw CatalogException.schema(resource.title)
            val date = date(text(row, if (resource == Resource.TPEX_COMPANY) "Date" else "出表日期", resource), resource)
            rows += Instrument(code, name, resource.kind, date, resource.market, resource.backtestSupported)
        }
        return rows.sortedBy { it.code }
    }
    private fun text(row: JsonNode, field: String, resource: Resource): String {
        val value = row.path(field)
        if (!value.isTextual || value.asText().isBlank()) throw CatalogException.schema(resource.title)
        return value.asText().trim()
    }
    private fun optionalText(row: JsonNode, field: String, fallback: String): String {
        val value = row.path(field)
        return if (value.isTextual && value.asText().isNotBlank()) value.asText().trim() else fallback
    }
    private fun date(value: String, resource: Resource): String = try {
        val parsed = when {
            value.matches(Regex("[0-9]{7}")) -> LocalDate.of(value.substring(0, 3).toInt() + 1911, value.substring(3, 5).toInt(), value.substring(5, 7).toInt())
            value.matches(Regex("[0-9]{8}")) -> LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
            value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) -> LocalDate.parse(value)
            value.matches(Regex("[0-9]{2,4}/[0-9]{1,2}/[0-9]{1,2}")) -> value.split("/").let { parts -> LocalDate.of(parts[0].toInt() + if (parts[0].length < 4) 1911 else 0, parts[1].toInt(), parts[2].toInt()) }
            else -> throw IllegalArgumentException("Unrecognized stamp")
        }
        require(!parsed.isBefore(LocalDate.of(1912, 1, 1)))
        parsed.toString()
    } catch (_: RuntimeException) { throw CatalogException.schema(resource.title) }
}
