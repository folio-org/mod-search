package org.folio.search.service.catchup;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.service.reindex.ReindexStatusService;
import org.folio.search.service.reindex.jdbc.TenantRepository;
import org.folio.search.utils.KafkaConstants;
import org.folio.search.service.EgressExecutionContextService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Manages the Phase 1 → Phase 2 transition in the v2 background indexer.
 *
 * <p>While {@code CATCH_UP_ENABLED=true}, live-event Kafka listeners are started with
 * {@code autoStartup=false} so they do not compete with the full reindex (Phase 1).
 * This service polls the per-tenant reindex status every
 * {@code CATCH_UP_CHECK_INTERVAL_MS} ms (default 30 s). Once every registered tenant
 * reports merge as completed, all live-event listener containers are started and
 * real-time catch-up (Phase 2) begins automatically.
 *
 * <p>Active only when {@code CATCH_UP_ENABLED=true} ({@code folio.catch-up.enabled}).
 * v1 deployments leave this property at its default ({@code false}) and are unaffected.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "folio.catch-up.enabled", havingValue = "true")
public class CatchUpPhaseManager {

  static final List<String> CATCH_UP_LISTENER_IDS = List.of(
    KafkaConstants.EVENT_LISTENER_ID,
    KafkaConstants.INDEX_INSTANCE_LISTENER_ID,
    KafkaConstants.AUTHORITY_LISTENER_ID,
    KafkaConstants.BROWSE_CONFIG_DATA_LISTENER_ID,
    KafkaConstants.LOCATION_LISTENER_ID,
    KafkaConstants.LINKED_DATA_LISTENER_ID,
    KafkaConstants.INSTANCE_SHARING_COMPLETE_LISTENER_ID
  );

  private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;
  private final TenantRepository tenantRepository;
  private final ReindexStatusService reindexStatusService;
  private final EgressExecutionContextService executionService;

  private final AtomicBoolean catchUpActive = new AtomicBoolean(false);

  @Scheduled(fixedDelayString = "${folio.catch-up.check-interval-ms:30000}")
  public void checkAndActivateCatchUp() {
    if (catchUpActive.get()) {
      return;
    }

    var tenantIds = tenantRepository.fetchDataTenantIds();
    if (tenantIds.isEmpty()) {
      log.debug("checkAndActivateCatchUp:: No tenants registered yet, skipping");
      return;
    }

    boolean allCompleted = tenantIds.stream().allMatch(this::isMergeCompletedForTenant);

    if (allCompleted) {
      log.info("checkAndActivateCatchUp:: Full reindex completed for all tenants [tenants: {}]. "
        + "Activating real-time catch-up listeners.", tenantIds);
      activateCatchUpListeners();
      catchUpActive.set(true);
    } else {
      log.debug("checkAndActivateCatchUp:: Full reindex still in progress — catch-up listeners remain paused");
    }
  }

  public boolean isCatchUpActive() {
    return catchUpActive.get();
  }

  /**
   * Stops all catch-up listener containers. Call this at the start of the maintenance window
   * before running the reconciliation check. The stop is immediate — no in-flight batches are
   * waited on, so call after Kafka consumer lag has reached zero.
   */
  public void stop() {
    log.info("stop:: Stopping catch-up listeners");
    for (var listenerId : CATCH_UP_LISTENER_IDS) {
      var container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
      if (container != null && container.isRunning()) {
        log.info("stop:: Stopping listener [id: {}]", listenerId);
        container.stop();
      }
    }
    catchUpActive.set(false);
    log.info("stop:: All catch-up listeners stopped. Safe to run reconciliation.");
  }

  private boolean isMergeCompletedForTenant(String tenantId) {
    try {
      return Boolean.TRUE.equals(
        executionService.execute(tenantId, reindexStatusService::isMergeCompleted));
    } catch (Exception e) {
      log.warn("checkAndActivateCatchUp:: Cannot check reindex status [tenantId: {}, error: {}]",
        tenantId, e.getMessage());
      return false;
    }
  }

  private void activateCatchUpListeners() {
    for (var listenerId : CATCH_UP_LISTENER_IDS) {
      var container = kafkaListenerEndpointRegistry.getListenerContainer(listenerId);
      if (container == null) {
        log.warn("activateCatchUpListeners:: Listener container not found [id: {}]", listenerId);
        continue;
      }
      if (!container.isRunning()) {
        log.info("activateCatchUpListeners:: Starting catch-up listener [id: {}]", listenerId);
        container.start();
      } else {
        log.debug("activateCatchUpListeners:: Listener already running [id: {}]", listenerId);
      }
    }
    log.info("activateCatchUpListeners:: Phase 2 active — v2 instance is now in real-time catch-up mode");
  }
}
