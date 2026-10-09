package uk.kuronekoli.strategylab.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(name = "catalog_instrument", indexes = [Index(name = "idx_catalog_instrument_code", columnList = "code"), Index(name = "idx_catalog_instrument_name", columnList = "name")])
open class CatalogInstrumentEntity(
    @field:Id @field:Column(length = 80, nullable = false) var id: String = "",
    @field:Column(length = 12, nullable = false) var code: String = "",
    @field:Column(length = 200, nullable = false) var name: String = "",
    @field:Column(length = 16, nullable = false) var kind: String = "",
    @field:Column(length = 16, nullable = false) var market: String = "",
    @field:Column(name = "as_of", length = 10, nullable = false) var asOf: String = "",
    @field:Column(name = "backtest_supported", nullable = false) var backtestSupported: Boolean = false,
    @field:Column(name = "source_id", length = 40, nullable = false) var sourceId: String = ""
)
