package uk.kuronekoli.strategylab.catalog;

import java.util.List;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Instrument;
import uk.kuronekoli.strategylab.catalog.InstrumentCatalogResponse.Source;

interface InstrumentCatalogStore {
  boolean isEmpty();
  List<Instrument> matching(String query);
  List<Source> sources();
  void replace(List<InstrumentCatalogClient.Snapshot> snapshots);
}
