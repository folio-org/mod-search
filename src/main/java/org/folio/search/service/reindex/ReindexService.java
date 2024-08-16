package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.InventoryService;
import org.folio.search.model.types.ReindexStatus;
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

    if (consortiumService.isMemberTenantInConsortium(tenantId)) {
      throw new RequestValidationException(
        "Not allowed to run reindex from member tenant of consortium environment", "tenantId", tenantId);
    }

    mergeRangeService.deleteAllRangeRecords();
    statusService.recreateStatusRecords(ReindexStatus.MERGE_IN_PROGRESS);

    mergeRangeService.createMergeRanges(tenantId);
    publishRecordsRange();

    //CompletableFuture.runAsync(() -> {
    //  mergeRangeService.createMergeRanges(tenantId);
    //  processForConsortium(tenantId);
    //})
    //  .thenRun(this::publishRecordsRange);

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
      log.warn("Skip creating merge ranges for [tenant: {}]. Exception: {}", tenantId, e.getMessage());
      statusService.updateMergeRangesFailed();
    }
  }

  private void publishRecordsRange() {
    for (var entityType : MERGE_RANGE_ENTITY_TYPES) {
      var rangeEntities = mergeRangeService.fetchMergeRanges(entityType);
      statusService.updateMergeRangesStarted(entityType, rangeEntities.size());

      for (var rangeEntity : rangeEntities) {
        try {
          inventoryService.publishReindexRecordsRange(rangeEntity);
        } catch (FolioIntegrationException e) {
          log.error("Failed to publish records range entity [rangeEntity: {}]. Exception: {}",
            rangeEntity, e.getMessage());
          statusService.updateMergeRangesFailed();
          return;
        }
      }
    }
  }
}