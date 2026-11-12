package org.folio.search.service.indexrebuild.derived;

import java.util.List;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.springframework.stereotype.Component;

/**
 * Strategies of all targets derived from another index.
 */
@Component
public class DerivedRebuildStrategies {

  private final List<DerivedRebuildStrategy> strategies;

  public DerivedRebuildStrategies(List<DerivedRebuildStrategy> strategies) {
    this.strategies = List.copyOf(strategies);
  }

  /**
   * Returns the strategy of a target.
   *
   * @throws IllegalArgumentException if the target isn't derived from another index
   */
  public DerivedRebuildStrategy get(IndexRebuildTarget target) {
    return strategies.stream()
      .filter(strategy -> strategy.target() == target)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("Not derived from another index: " + target));
  }

  /**
   * Returns strategies of targets built from the given index.
   */
  public List<DerivedRebuildStrategy> findBySource(IndexRebuildTarget source) {
    return strategies.stream().filter(strategy -> strategy.source() == source).toList();
  }
}
