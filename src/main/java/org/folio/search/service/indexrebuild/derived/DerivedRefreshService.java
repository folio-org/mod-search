package org.folio.search.service.indexrebuild.derived;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.service.indexrebuild.IndexFingerprintService;
import org.folio.search.service.indexrebuild.IndexRebuildReadinessService;
import org.folio.search.service.indexrebuild.IndexRebuildTarget;
import org.folio.search.service.indexrebuild.jdbc.DirtyKeyRepository;
import org.folio.search.service.indexrebuild.jdbc.IndexRebuildStateRepository;
import org.folio.search.service.indexrebuild.model.IndexRebuildState;
import org.folio.search.service.indexrebuild.model.IndexRebuildStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rebuilds documents of queued keys of a derived index. A document is built from source documents of all tenants, so
 * changes wait while the source index of any tenant isn't ready or a rebuild of the derived index is active, and are
 * applied afterwards. Each batch runs in a transaction holding the queue lock of the index, so module instances don't
 * rebuild the same documents concurrently.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DerivedRefreshService {

  private static final Set<IndexRebuildStatus> ACTIVE =
    Set.of(IndexRebuildStatus.IN_PROGRESS, IndexRebuildStatus.FINALIZING);

  private final DerivedRebuildStrategies strategies;
  private final DirtyKeyRepository dirtyKeyRepository;
  private final DerivedIndexReader reader;
  private final DerivedDocumentWriter documentWriter;
  private final IndexRebuildStateRepository stateRepository;
  private final IndexRebuildReadinessService readinessService;
  private final IndexFingerprintService fingerprintService;
  private final SearchConfigurationProperties searchConfiguration;
  private final TransactionTemplate transactionTemplate;

  /**
   * Processes queued keys of a derived index. Must be called in the context of the tenant holding the queue.
   */
  public void refresh(IndexRebuildTarget target, String tenantId) {
    var strategy = strategies.get(target);
    if (!strategy.isEnabled()) {
      log.debug("refresh:: index is disabled [resource: {}, tenant: {}]", target.getResource(), tenantId);
      return;
    }
    var tenants = findReadyTenants(strategy);
    if (tenants.isEmpty()) {
      return;
    }
    var batchSize = searchConfiguration.getIndexing().getSubResourceBatchSize();
    var total = 0;
    int processed;
    do {
      processed = Objects.requireNonNullElse(
        transactionTemplate.execute(status -> refreshBatch(strategy, tenantId, tenants, batchSize)), 0);
      total += processed;
    } while (processed == batchSize);
    if (total > 0) {
      log.info("refresh:: changed keys refreshed [resource: {}, tenant: {}, keys: {}]", target.getResource(), tenantId,
        total);
    }
  }

  /**
   * Rebuilds one batch of queued keys. Keys queued again after the batch started stay in the queue.
   *
   * @return number of processed keys, 0 if the queue is locked by another module instance
   */
  private int refreshBatch(DerivedRebuildStrategy strategy, String tenantId, List<String> tenants, int batchSize) {
    var target = strategy.target();
    if (!dirtyKeyRepository.tryLock(target)) {
      log.debug("refreshBatch:: {} keys are refreshed by another instance [tenant: {}]", target.getResource(),
        tenantId);
      return 0;
    }
    var startedAt = dirtyKeyRepository.currentTime();
    var keys = dirtyKeyRepository.findBatch(target, tenants, batchSize);
    if (!keys.isEmpty()) {
      reader.refresh(strategy.source(), tenantId);
      documentWriter.rebuild(strategy, tenantId, keys);
      dirtyKeyRepository.delete(target, keys, tenants, startedAt);
    }
    return keys.size();
  }

  /**
   * Finds tenants whose queued keys can be processed: none while the source index of any tenant isn't ready, because a
   * document would be built without that tenant's source documents, or while a rebuild of the target is active.
   */
  private List<String> findReadyTenants(DerivedRebuildStrategy strategy) {
    var fingerprint = fingerprintService.current(strategy.source());
    var sourceStates = stateRepository.findAll(strategy.source());
    var sourceReady = sourceStates.stream().allMatch(state -> readinessService.isReady(state, fingerprint));
    var runActive = stateRepository.findAll(strategy.target()).stream()
      .anyMatch(state -> ACTIVE.contains(state.status()));
    if (!sourceReady || runActive) {
      log.debug("findReadyTenants:: changed keys wait [resource: {}, sourceReady: {}, runActive: {}]",
        strategy.target().getResource(), sourceReady, runActive);
      return List.of();
    }
    return sourceStates.stream().map(IndexRebuildState::tenantId).toList();
  }
}
