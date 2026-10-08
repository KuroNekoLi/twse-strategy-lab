package uk.kuronekoli.strategylab.catalog;

import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source;

@Repository
class JpaInstrumentCatalogStore implements InstrumentCatalogStore {
  private final CatalogInstrumentRepository instruments;
  private final CatalogSourceRepository sources;
  JpaInstrumentCatalogStore(CatalogInstrumentRepository instruments, CatalogSourceRepository sources) {
    this.instruments = instruments; this.sources = sources;
  }
  @Override @Transactional(readOnly = true)
  public boolean isEmpty() { return instruments.count() == 0; }
  @Override @Transactional(readOnly = true)
  public List<Instrument> matching(String query) {
    return instruments.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(query, query).stream()
        .map(row -> new Instrument(row.code, row.name, row.kind, row.asOf, row.market, row.backtestSupported)).toList();
  }
  @Override @Transactional(readOnly = true)
  public List<Source> sources() { return sources.findAll().stream().map(CatalogSourceEntity::toRecord).toList(); }
  @Override @Transactional
  public void replace(List<InstrumentCatalogClient.Snapshot> snapshots) {
    // The complete upstream set is parsed before this transaction; a failed source never erases the last good catalog.
    instruments.deleteAllInBatch();
    sources.deleteAllInBatch();
    sources.saveAll(snapshots.stream().map(snapshot -> new CatalogSourceEntity(snapshot.source())).toList());
    List<CatalogInstrumentEntity> rows = snapshots.stream().flatMap(snapshot -> snapshot.rows().stream().map(item ->
        new CatalogInstrumentEntity(snapshot.source().id() + ":" + item.code(), item.code(), item.name(), item.kind(),
            item.market(), item.asOf(), item.backtestSupported(), snapshot.source().id()))).toList();
    instruments.saveAll(rows);
  }
}
