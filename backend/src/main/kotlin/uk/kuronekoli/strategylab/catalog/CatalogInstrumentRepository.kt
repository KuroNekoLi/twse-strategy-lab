package uk.kuronekoli.strategylab.catalog

import org.springframework.data.jpa.repository.JpaRepository

interface CatalogInstrumentRepository : JpaRepository<CatalogInstrumentEntity, String> {
    fun findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(code: String, name: String): List<CatalogInstrumentEntity>
}
