package uk.kuronekoli.strategylab.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_instrument", indexes = {
    @Index(name = "idx_catalog_instrument_code", columnList = "code"),
    @Index(name = "idx_catalog_instrument_name", columnList = "name")})
class CatalogInstrumentEntity {
  @Id @Column(length = 80, nullable = false)
  String id;
  @Column(length = 12, nullable = false)
  String code;
  @Column(length = 200, nullable = false)
  String name;
  @Column(length = 16, nullable = false)
  String kind;
  @Column(length = 16, nullable = false)
  String market;
  @Column(name = "as_of", length = 10, nullable = false)
  String asOf;
  @Column(name = "backtest_supported", nullable = false)
  boolean backtestSupported;
  @Column(name = "source_id", length = 40, nullable = false)
  String sourceId;
  protected CatalogInstrumentEntity() {}
  CatalogInstrumentEntity(String id, String code, String name, String kind, String market, String asOf, boolean backtestSupported, String sourceId) {
    this.id = id; this.code = code; this.name = name; this.kind = kind; this.market = market;
    this.asOf = asOf; this.backtestSupported = backtestSupported; this.sourceId = sourceId;
  }
}
