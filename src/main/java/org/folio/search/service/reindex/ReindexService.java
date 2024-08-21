package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexService {

  private final ConsortiumTenantService consortiumService;
  private final SystemUserScopedExecutionService executionService;
  private final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexStatusService statusService;
  private final InventoryService inventoryService;
  private final ExecutorService reindexExecutor;

  public ReindexService(ConsortiumTenantService consortiumService,
                        SystemUserScopedExecutionService executionService,
                        ReindexMergeRangeIndexService mergeRangeService,
                        ReindexStatusService statusService,
                        InventoryService inventoryService,
                        ExecutorService reindexExecutor) {
    this.consortiumService = consortiumService;
    this.executionService = executionService;
    this.mergeRangeService = mergeRangeService;
    this.statusService = statusService;
    this.inventoryService = inventoryService;
    this.reindexExecutor = reindexExecutor;
  }

  public CompletableFuture<Void> initFullReindex(String tenantId) {
    log.info("submit full reindex process");

    var central = consortiumService.getCentralTenant(tenantId);
    if (central.isPresent() && !central.get().equals(tenantId)) {
      throw new RequestValidationException(
        "Not allowed to run reindex from member tenant of consortium environment", "tenantId", tenantId);
    }

    mergeRangeService.deleteAllRangeRecords();
    statusService.recreateMergeStatusRecords();

    var future = CompletableFuture.runAsync(() -> {
      var rangesForAllTenants = Stream.of(
          mergeRangeService.createMergeRanges(tenantId),
          processForConsortium(tenantId)
        )
        .flatMap(List::stream)
        .toList();
      mergeRangeService.saveMergeRanges(rangesForAllTenants);
    }, reindexExecutor)
      .thenRun(this::publishRecordsRange)
      .handle((unused, throwable) -> {
        if (throwable != null) {
          log.error("full reindex process failed: {}", throwable.getMessage());
          statusService.updateReindexMergeFailed();
        }
        return unused;
      });

    log.info("full reindex process submitted");
    return future;
  }

  private List<MergeRangeEntity> processForConsortium(String tenantId) {
    List<MergeRangeEntity> mergeRangeEntities = new ArrayList<>();
    var memberTenants = consortiumService.getConsortiumTenants(tenantId);
    for (var memberTenant : memberTenants) {
      executionService.executeAsyncSystemUserScoped(memberTenant, () ->
        mergeRangeEntities.addAll(mergeRangeService.createMergeRanges(memberTenant)));
    }
    return mergeRangeEntities;
  }

  private void publishRecordsRange() {
    for (var entityType : MERGE_RANGE_ENTITY_TYPES) {
      var rangeEntities = mergeRangeService.fetchMergeRanges(entityType);
      if (CollectionUtils.isNotEmpty(rangeEntities)) {
        log.info("Publishing {} {} range entities", rangeEntities.size(), entityType);
        statusService.updateReindexMergeStarted(entityType, rangeEntities.size());
        for (var rangeEntity : rangeEntities) {
          inventoryService.publishReindexRecordsRange(rangeEntity);
        }
      }
    }
  }
}
