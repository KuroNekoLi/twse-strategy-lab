package uk.kuronekoli.strategylab.catalog;

import java.util.ArrayList;
import java.util.List;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source;

/** Deterministic test adapter; production uses the JPA store. */
final class MemoryInstrumentCatalogStore implements InstrumentCatalogStore {
  private List<Instrument> instruments = List.of();
  private List<Source> sources = List.of();
  @Override public boolean isEmpty() { return instruments.isEmpty(); }
  @Override public List<Instrument> matching(String query) {
    String needle = query.toLowerCase(java.util.Locale.ROOT);
    return instruments.stream().filter(item -> item.code().toLowerCase(java.util.Locale.ROOT).contains(needle)
        || item.name().toLowerCase(java.util.Locale.ROOT).contains(needle)).toList();
  }
  @Override public List<Source> sources() { return sources; }
  @Override public void replace(List<InstrumentCatalogClient.Snapshot> snapshots) {
    instruments = new ArrayList<>(snapshots.stream().flatMap(snapshot -> snapshot.rows().stream()).toList());
    sources = snapshots.stream().map(InstrumentCatalogClient.Snapshot::source).toList();
  }
}
