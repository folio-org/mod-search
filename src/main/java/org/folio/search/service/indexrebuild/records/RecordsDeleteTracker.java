package org.folio.search.service.indexrebuild.records;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.DeletedRecordRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

/**
 * Remembers records deleted while a rebuild of their index is running. An older record can still arrive, and a
 * create-only write finds no document to conflict with, so these records are removed when the run ends.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RecordsDeleteTracker {

  private final IndexRebuildStateRepository stateRepository;
  private final DeletedRecordRepository deletedRecordRepository;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;

  /**
   * Tracks events of records of a tenant. Must be called before they are applied to the index. Only deletes and creates
   * are tracked, so batches of updates don't query the database.
   */
  public void track(IndexRebuildTarget target, String tenantId, List<ResourceEvent> events) {
    var tracked = events.stream().filter(RecordsDeleteTracker::isTracked).toList();
    if (tracked.isEmpty()) {
      return;
    }
    try {
      consortiumTenantExecutor.run(() -> {
        if (stateRepository.isTracking(target, tenantId)) {
          tracked.forEach(event -> trackEvent(target, tenantId, event));
          log.debug("track:: tracked deletes and creates during a run [resource: {}, tenant: {}, events: {}]",
            target.getResource(), tenantId, tracked.size());
        }
      });
    } catch (BadSqlGrammarException e) {
      // tables are created by the tenant upgrade, until then no rebuild can be running
      log.warn("track:: index rebuild tables are missing, module upgrade may be in progress [tenant: {}]", tenantId);
    }
  }

  private static boolean isTracked(ResourceEvent event) {
    return event.getType() == ResourceEventType.DELETE || event.getType() == ResourceEventType.CREATE;
  }

  private void trackEvent(IndexRebuildTarget target, String tenantId, ResourceEvent event) {
    if (event.getType() == ResourceEventType.DELETE) {
      deletedRecordRepository.add(target, tenantId, List.of(event.getId()));
    } else if (event.getType() == ResourceEventType.CREATE) {
      deletedRecordRepository.remove(target, tenantId, List.of(event.getId()));
    }
  }
}
