package org.folio.search.service.indexrebuild.records;

import java.util.List;
import java.util.Optional;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.springframework.stereotype.Component;

/**
 * Strategies of all targets rebuilt from inventory records.
 */
@Component
public class RecordsRebuildStrategies {

  private final List<RecordsRebuildStrategy> strategies;

  public RecordsRebuildStrategies(List<RecordsRebuildStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  /**
   * Returns the strategy of a target.
   *
   * @throws IllegalArgumentException if the target isn't rebuilt from records
   */
  public RecordsRebuildStrategy get(IndexRebuildTarget target) {
    return strategies.stream()
      .filter(strategy -> strategy.target() == target)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Not rebuilt from records: " + target));
  }

  public Optional<RecordsRebuildStrategy> find(ReindexRecordType recordType) {
    return strategies.stream().filter(strategy -> strategy.reindexRecordType() == recordType).findFirst();
  }
}
