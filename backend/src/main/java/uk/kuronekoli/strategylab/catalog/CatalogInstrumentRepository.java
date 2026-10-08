package uk.kuronekoli.strategylab.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface CatalogInstrumentRepository extends JpaRepository<CatalogInstrumentEntity, String> {
  List<CatalogInstrumentEntity> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(String code, String name);
}
