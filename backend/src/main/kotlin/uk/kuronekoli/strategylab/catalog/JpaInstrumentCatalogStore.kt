package uk.kuronekoli.strategylab.catalog

import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source

@Repository
class JpaInstrumentCatalogStore(private val instruments: CatalogInstrumentRepository, private val sourcesRepository: CatalogSourceRepository) : InstrumentCatalogStore {
    @Transactional(readOnly = true) override fun isEmpty() = instruments.count() == 0L
    @Transactional(readOnly = true) override fun matching(query: String): List<Instrument> = instruments.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(query, query).map { Instrument(it.code, it.name, it.kind, it.asOf, it.market, it.backtestSupported) }
    @Transactional(readOnly = true) override fun sources(): List<Source> = sourcesRepository.findAll().map { it.toRecord() }
    @Transactional override fun replace(snapshots: List<InstrumentCatalogClient.Snapshot>) {
        // Parse the complete upstream set before this transaction, so a failed source never erases the last good catalog.
        instruments.deleteAllInBatch()
        sourcesRepository.deleteAllInBatch()
        sourcesRepository.saveAll(snapshots.map { CatalogSourceEntity(it.source) })
        val rows = snapshots.flatMap { snapshot -> snapshot.rows.map { item -> CatalogInstrumentEntity("${snapshot.source.id}:${item.code}", item.code, item.name, item.kind, item.market, item.asOf, item.backtestSupported, snapshot.source.id) } }
        instruments.saveAll(rows)
    }
}
