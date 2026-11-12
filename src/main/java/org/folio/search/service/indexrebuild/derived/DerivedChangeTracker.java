package org.folio.search.service.indexrebuild.derived;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.DirtyKeyRepository;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

/**
 * Queues keys of derived indexes affected by changes of their source index. Must be called after the changes are
 * written to the source index, because the queue is processed by reading it.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class DerivedChangeTracker {

  private final DerivedRebuildStrategies strategies;
  private final DirtyKeyRepository dirtyKeyRepository;
  private final DerivedIndexReader reader;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;

  /**
   * Queues keys of old and new versions of records changed by events of the source index.
   */
  public void track(IndexRebuildTarget source, String tenantId, List<ResourceEvent> events) {
    for (var strategy : trackingStrategies(source)) {
      var keys = new HashSet<String>();
      events.forEach(event -> {
        addKeys(strategy, keys, event.getNew());
        addKeys(strategy, keys, event.getOld());
      });
      enqueue(strategy.target(), tenantId, keys);
    }
  }

  /**
   * Queues keys of source documents matching the query, for changes made by a bulk update of the source index.
   */
  public void trackMatching(IndexRebuildTarget source, String tenantId, QueryBuilder query) {
    var tracking = trackingStrategies(source);
    if (tracking.isEmpty()) {
      return;
    }
    reader.refresh(source, tenantId);
    tracking.forEach(strategy -> enqueue(strategy.target(), tenantId, reader.findKeys(strategy, tenantId, query)));
  }

  private List<DerivedRebuildStrategy> trackingStrategies(IndexRebuildTarget source) {
    return strategies.findBySource(source).stream().filter(DerivedRebuildStrategy::isTrackingEnabled).toList();
  }

  private void enqueue(IndexRebuildTarget target, String tenantId, Set<String> keys) {
    if (keys.isEmpty()) {
      return;
    }
    try {
      consortiumTenantExecutor.run(() -> dirtyKeyRepository.add(target, tenantId, keys));
      log.debug("enqueue:: changed keys queued [resource: {}, tenant: {}, keys: {}]", target.getResource(), tenantId,
        keys.size());
    } catch (BadSqlGrammarException e) {
      // the queue is created by the tenant upgrade, a reindex of the derived index is required after it anyway
      log.warn("enqueue:: queue of changed keys is missing, module upgrade may be in progress [resource: {}, "
               + "tenant: {}]", target.getResource(), tenantId);
    }
  }

  private static void addKeys(DerivedRebuildStrategy strategy, Set<String> keys, Object sourceRecord) {
    if (sourceRecord instanceof Map<?, ?> map) {
      keys.addAll(strategy.keysOf(map));
    }
  }
}
