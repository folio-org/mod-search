package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

import java.util.concurrent.CompletableFuture;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.integration.InventoryService;
import org.folio.search.service.consortium.ConsortiumTenantsService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexService {

  private final ConsortiumTenantsService consortiumService;
  private final SystemUserScopedExecutionService executionService;
  private final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexStatusService statusService;
  private final InventoryService inventoryService;

  public ReindexService(ConsortiumTenantsService consortiumService,
                        SystemUserScopedExecutionService executionService,
                        ReindexMergeRangeIndexService mergeRangeService,
                        ReindexStatusService statusService, InventoryService inventoryService) {
    this.consortiumService = consortiumService;
    this.executionService = executionService;
    this.mergeRangeService = mergeRangeService;
    this.statusService = statusService;
    this.inventoryService = inventoryService;
  }

  public void initFullReindex(String tenantId) {
    log.info("submit full reindex process");

    mergeRangeService.deleteAllRangeRecords();
    statusService.recreateStatusRecords();

    CompletableFuture.runAsync(() -> {
      mergeRangeService.createMergeRanges(tenantId);
      processForConsortium(tenantId);
    })
      .thenRun(this::publishRecordsRange);

    log.info("full reindex process submitted");
  }

  private void processForConsortium(String tenantId) {
    try {
      var memberTenants = consortiumService.getConsortiumTenants(tenantId);
      for (var memberTenant : memberTenants) {
        executionService.executeAsyncSystemUserScoped(memberTenant, () ->
          mergeRangeService.createMergeRanges(memberTenant));
      }
    } catch (FolioIntegrationException e) {
      log.warn("Skip creating merge ranges for [tenant: {}]. Exception: {}", tenantId, e);
    }
  }

  private void publishRecordsRange() {
    MERGE_RANGE_ENTITY_TYPES.forEach(entityType -> {
      var rangeEntities = mergeRangeService.fetchMergeRanges(entityType);
      var count = mergeRangeService.fetchRangeEntitiesCount(entityType);
      statusService.updateMergeRangesStarted(entityType, count);

      for (var rangeEntity : rangeEntities) {
        try {
          inventoryService.publishReindexRecordsRange(rangeEntity);
        } catch (FolioIntegrationException e) {
          log.error("Failed to publish records range entity [rangeEntity: {}]. Exception: {}", rangeEntity, e);
          statusService.updateMergeRangesFailed(entityType);
          return;
        }
      }
    });
  }
}
