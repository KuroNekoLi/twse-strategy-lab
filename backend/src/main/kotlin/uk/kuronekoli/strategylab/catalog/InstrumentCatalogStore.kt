package uk.kuronekoli.strategylab.catalog

import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source

interface InstrumentCatalogStore {
    fun isEmpty(): Boolean
    fun matching(query: String): List<Instrument>
    fun sources(): List<Source>
    fun replace(snapshots: List<InstrumentCatalogClient.Snapshot>)
}
