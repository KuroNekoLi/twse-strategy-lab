package uk.kuronekoli.strategylab.catalog

import org.springframework.data.jpa.repository.JpaRepository

interface CatalogSourceRepository : JpaRepository<CatalogSourceEntity, String>
