package uk.kuronekoli.strategylab.catalog;

import java.util.List;

public record InstrumentCatalogResponse(String query, int limit, int totalMatches, List<Instrument> items,
    List<Source> sources, List<String> limitations) {
  /** Whitelist: no corporate contact, individual name, address or identifier fields. */
  public record Instrument(String code, String name, String kind, String asOf) {}
  public record Source(String id, String title, String provider, String datasetUrl, String resourceUrl,
      String license, String licenseUrl, String updateFrequency, String fetchedAt,
      String asOfFrom, String asOfTo, long cacheTtlSeconds) {}
}
