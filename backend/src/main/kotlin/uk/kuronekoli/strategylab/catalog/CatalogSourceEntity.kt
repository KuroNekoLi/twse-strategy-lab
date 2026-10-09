package uk.kuronekoli.strategylab.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "catalog_source")
open class CatalogSourceEntity(
    @field:Id @field:Column(length = 40, nullable = false) var id: String = "",
    @field:Column(nullable = false, length = 120) var title: String = "",
    @field:Column(nullable = false, length = 80) var provider: String = "",
    @field:Column(nullable = false, length = 300) var datasetUrl: String = "",
    @field:Column(nullable = false, length = 300) var resourceUrl: String = "",
    @field:Column(nullable = false, length = 100) var license: String = "",
    @field:Column(nullable = false, length = 200) var licenseUrl: String = "",
    @field:Column(nullable = false, length = 16) var updateFrequency: String = "",
    @field:Column(nullable = false, length = 30) var fetchedAt: String = "",
    @field:Column(nullable = false, length = 10) var asOfFrom: String = "",
    @field:Column(nullable = false, length = 10) var asOfTo: String = "",
    @field:Column(nullable = false) var cacheTtlSeconds: Long = 0
) {
    constructor(source: InstrumentCatalogResponse.Source) : this(source.id, source.title, source.provider, source.datasetUrl, source.resourceUrl, source.license, source.licenseUrl, source.updateFrequency, source.fetchedAt, source.asOfFrom, source.asOfTo, source.cacheTtlSeconds)
    fun toRecord() = InstrumentCatalogResponse.Source(id, title, provider, datasetUrl, resourceUrl, license, licenseUrl, updateFrequency, fetchedAt, asOfFrom, asOfTo, cacheTtlSeconds)
}
