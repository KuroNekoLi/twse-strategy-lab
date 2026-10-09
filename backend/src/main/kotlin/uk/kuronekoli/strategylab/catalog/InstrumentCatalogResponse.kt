package uk.kuronekoli.strategylab.catalog

data class InstrumentCatalogResponse(val query: String, val limit: Int, val totalMatches: Int, val items: List<Instrument>, val sources: List<Source>, val limitations: List<String>) {
    /** Whitelist: no corporate contact, individual name, address or identifier fields. */
    data class Instrument(val code: String, val name: String, val kind: String, val asOf: String, val market: String, val backtestSupported: Boolean)
    data class Source(val id: String, val title: String, val provider: String, val datasetUrl: String, val resourceUrl: String, val license: String, val licenseUrl: String, val updateFrequency: String, val fetchedAt: String, val asOfFrom: String, val asOfTo: String, val cacheTtlSeconds: Long)
}
