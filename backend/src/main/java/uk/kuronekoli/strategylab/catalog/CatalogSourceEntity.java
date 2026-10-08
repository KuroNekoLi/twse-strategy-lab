package uk.kuronekoli.strategylab.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_source")
class CatalogSourceEntity {
  @Id @Column(length = 40, nullable = false)
  String id;
  @Column(nullable = false, length = 120) String title;
  @Column(nullable = false, length = 80) String provider;
  @Column(nullable = false, length = 300) String datasetUrl;
  @Column(nullable = false, length = 300) String resourceUrl;
  @Column(nullable = false, length = 100) String license;
  @Column(nullable = false, length = 200) String licenseUrl;
  @Column(nullable = false, length = 16) String updateFrequency;
  @Column(nullable = false, length = 30) String fetchedAt;
  @Column(nullable = false, length = 10) String asOfFrom;
  @Column(nullable = false, length = 10) String asOfTo;
  @Column(nullable = false) long cacheTtlSeconds;
  protected CatalogSourceEntity() {}
  CatalogSourceEntity(InstrumentCatalogResponse.Source source) {
    id = source.id(); title = source.title(); provider = source.provider(); datasetUrl = source.datasetUrl();
    resourceUrl = source.resourceUrl(); license = source.license(); licenseUrl = source.licenseUrl();
    updateFrequency = source.updateFrequency(); fetchedAt = source.fetchedAt(); asOfFrom = source.asOfFrom();
    asOfTo = source.asOfTo(); cacheTtlSeconds = source.cacheTtlSeconds();
  }
  InstrumentCatalogResponse.Source toRecord() {
    return new InstrumentCatalogResponse.Source(id, title, provider, datasetUrl, resourceUrl, license, licenseUrl,
        updateFrequency, fetchedAt, asOfFrom, asOfTo, cacheTtlSeconds);
  }
}
