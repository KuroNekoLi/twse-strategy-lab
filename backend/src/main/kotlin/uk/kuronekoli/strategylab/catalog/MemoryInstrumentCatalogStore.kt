package uk.kuronekoli.strategylab.catalog

import java.util.Locale
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source

/** Deterministic test adapter; production uses the JPA store. */
class MemoryInstrumentCatalogStore : InstrumentCatalogStore {
    private var instruments: List<Instrument> = emptyList()
    private var sourceRecords: List<Source> = emptyList()
    override fun isEmpty() = instruments.isEmpty()
    override fun matching(query: String): List<Instrument> {
        val needle = query.lowercase(Locale.ROOT)
        return instruments.filter { it.code.lowercase(Locale.ROOT).contains(needle) || it.name.lowercase(Locale.ROOT).contains(needle) }
    }
    override fun sources() = sourceRecords
    override fun replace(snapshots: List<InstrumentCatalogClient.Snapshot>) {
        instruments = snapshots.flatMap { it.rows }
        sourceRecords = snapshots.map { it.source }
    }
}
