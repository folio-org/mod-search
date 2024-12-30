package org.folio.search.service.reindex;

import static org.folio.search.model.types.ReindexStatus.MERGE_FAILED;
import static org.folio.search.model.types.ReindexStatus.MERGE_IN_PROGRESS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.converter.ReindexEntityTypeMapper;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexService {

  private final ConsortiumTenantService consortiumService;
  private final SystemUserScopedExecutionService executionService;
  private final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexUploadRangeIndexService uploadRangeService;
  private final ReindexStatusService statusService;
  private final InventoryService inventoryService;
  private final ExecutorService reindexFullExecutor;
  private final ExecutorService reindexUploadExecutor;
  private final ExecutorService reindexPublisherExecutor;
  private final ReindexEntityTypeMapper entityTypeMapper;
  private final ReindexCommonService reindexCommonService;

  public ReindexService(ConsortiumTenantService consortiumService,
                        SystemUserScopedExecutionService executionService,
                        ReindexMergeRangeIndexService mergeRangeService,
                        ReindexUploadRangeIndexService uploadRangeService,
                        ReindexStatusService statusService,
                        InventoryService inventoryService,
                        @Qualifier("reindexFullExecutor") ExecutorService reindexFullExecutor,
                        @Qualifier("reindexUploadExecutor") ExecutorService reindexUploadExecutor,
                        @Qualifier("reindexPublisherExecutor") ExecutorService reindexPublisherExecutor,
                        ReindexEntityTypeMapper entityTypeMapper,
                        ReindexCommonService reindexCommonService) {
    this.consortiumService = consortiumService;
    this.executionService = executionService;
    this.mergeRangeService = mergeRangeService;
    this.uploadRangeService = uploadRangeService;
    this.statusService = statusService;
    this.inventoryService = inventoryService;
    this.reindexFullExecutor = reindexFullExecutor;
    this.reindexUploadExecutor = reindexUploadExecutor;
    this.reindexPublisherExecutor = reindexPublisherExecutor;
    this.entityTypeMapper = entityTypeMapper;
    this.reindexCommonService = reindexCommonService;
  }

  public CompletableFuture<Void> submitFullReindex(String tenantId, IndexSettings indexSettings) {
    log.info("submitFullReindex:: for [tenantId: {}]", tenantId);

    validateTenant("submitFullReindex", tenantId);

    reindexCommonService.deleteAllRecords();
    statusService.recreateMergeStatusRecords();

    var future = CompletableFuture.runAsync(() -> {
      mergeRangeService.truncateMergeRanges();
      var rangesForAllTenants = Stream.of(
          mergeRangeService.createMergeRanges(tenantId),
          processForConsortium(tenantId)
        )
        .flatMap(List::stream)
        .toList();
      mergeRangeService.saveMergeRanges(rangesForAllTenants);
    }, reindexFullExecutor)
      .thenRun(() -> publishRecordsRange(tenantId))
      .thenRun(() -> recreateIndices(tenantId, ReindexEntityType.supportUploadTypes(), indexSettings))
      .handle((unused, throwable) -> {
        if (throwable != null) {
          log.error("initFullReindex:: process failed [tenantId: {}, error: {}]", tenantId, throwable);
          statusService.updateReindexMergeFailed();
        }
        return unused;
      });

    log.info("submitFullReindex:: submitted [tenantId: {}]", tenantId);
    return future;
  }

  public CompletableFuture<Void> submitUploadReindex(String tenantId,
                                                     ReindexUploadDto reindexUploadDto) {
    var entityTypes = entityTypeMapper.convert(reindexUploadDto.getEntityTypes())
      .stream().filter(ReindexEntityType::isSupportsUpload).toList();
    return submitUploadReindex(tenantId, entityTypes, true, reindexUploadDto.getIndexSettings());
  }

  public CompletableFuture<Void> submitUploadReindex(String tenantId,
                                                     List<ReindexEntityType> entityTypes) {
    return submitUploadReindex(tenantId, entityTypes, false, null);
  }

  private CompletableFuture<Void> submitUploadReindex(String tenantId,
                                                     List<ReindexEntityType> entityTypes,
                                                     boolean recreateIndex,
                                                     IndexSettings indexSettings) {
    log.info("submitUploadReindex:: for [tenantId: {}, entities: {}]", tenantId, entityTypes);

    validateUploadReindex(tenantId, entityTypes);

    for (var reindexEntityType : entityTypes) {
      statusService.recreateUploadStatusRecord(reindexEntityType);
      if (recreateIndex) {
        reindexCommonService.recreateIndex(reindexEntityType, tenantId, indexSettings);
      }
    }

    var futures = new ArrayList<>();
    for (var entityType : entityTypes) {
      var future = CompletableFuture.runAsync(() ->
           uploadRangeService.prepareAndSendIndexRanges(entityType), reindexUploadExecutor)
        .handle((unused, throwable) -> {
          if (throwable != null) {
            log.error("reindex upload process failed: {}", throwable.getMessage());
            statusService.updateReindexUploadFailed(entityType);
          }
          return unused;
        });
      futures.add(future);
    }

    log.info("submitUploadReindex:: submitted [tenantId: {}]", tenantId);
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  public CompletableFuture<Void> submitFailedMergeRangesReindex(String tenantId) {
    log.info("submitFailedMergeRangesReindex:: for [tenantId: {}]", tenantId);

    validateTenant("submitFailedMergeRangesReindex", tenantId);
    var tenantIds = new ArrayList<String>();
    tenantIds.add(tenantId);
    var memberTenants = consortiumService.getConsortiumTenants(tenantId);
    tenantIds.addAll(memberTenants);

    var failedRanges = mergeRangeService.fetchFailedMergeRanges(tenantIds);
    if (CollectionUtils.isEmpty(failedRanges)) {
      log.info("submitFailedMergeRangesReindex:: no failed ranges found");
      return CompletableFuture.completedFuture(null);
    }

    log.info("submitFailedMergeRangesReindex:: for [tenantId: {}, count: {}]", tenantId, failedRanges.size());
    var entityTypes = failedRanges.stream()
      .map(MergeRangeEntity::getEntityType)
      .collect(Collectors.toSet());
    statusService.updateReindexMergeInProgress(entityTypes);

    var futures = new ArrayList<>();
    for (var rangeEntity : failedRanges) {
      var future = CompletableFuture.runAsync(() ->
        executionService.executeSystemUserScoped(rangeEntity.getTenantId(), () -> {
          inventoryService.publishReindexRecordsRange(rangeEntity);
          return null;
        }), reindexPublisherExecutor);
      futures.add(future);
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private void recreateIndices(String tenantId, List<ReindexEntityType> entityTypes, IndexSettings indexSettings) {
    for (var reindexEntityType : entityTypes) {
      reindexCommonService.recreateIndex(reindexEntityType, tenantId, indexSettings);
    }
  }

  private List<MergeRangeEntity> processForConsortium(String tenantId) {
    List<MergeRangeEntity> mergeRangeEntities = new ArrayList<>();
    var memberTenants = consortiumService.getConsortiumTenants(tenantId);
    for (var memberTenant : memberTenants) {
      mergeRangeEntities.addAll(
        executionService.executeSystemUserScoped(memberTenant, () -> mergeRangeService.createMergeRanges(memberTenant))
      );
    }
    return mergeRangeEntities;
  }

  private void publishRecordsRange(String tenantId) {
    var futures = new ArrayList<>();
    for (var entityType : ReindexEntityType.supportMergeTypes()) {
      var rangeEntities = mergeRangeService.fetchMergeRanges(entityType);
      if (CollectionUtils.isNotEmpty(rangeEntities)) {
        log.info("publishRecordsRange:: publishing merge ranges [tenant: {}, entityType: {}, count: {}]",
          tenantId, entityType, rangeEntities.size());

        statusService.updateReindexMergeStarted(entityType, rangeEntities.size());
        for (var rangeEntity : rangeEntities) {
          var publishFuture = CompletableFuture.runAsync(() ->
            executionService.executeSystemUserScoped(rangeEntity.getTenantId(), () -> {
              inventoryService.publishReindexRecordsRange(rangeEntity);
              return null;
            }), reindexPublisherExecutor);
          futures.add(publishFuture);
        }
      }
    }
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
  }

  private void validateUploadReindex(String tenantId, List<ReindexEntityType> entityTypes) {
    validateTenant("submitUploadReindex", tenantId);

    var statusesByType = statusService.getStatusesByType();

    var mergeNotComplete = statusesByType.entrySet().stream()
      .filter(status -> ReindexEntityType.supportMergeTypes().contains(status.getKey()))
      .filter(status -> status.getValue().equals(MERGE_IN_PROGRESS) || status.getValue().equals(MERGE_FAILED))
      .findFirst();
    if (mergeNotComplete.isPresent()) {
      var mergeEntityStatus = mergeNotComplete.get();
      // full reindex is either in progress or failed
      throw new RequestValidationException(
        "Merge phase is in progress or failed for: %s".formatted(mergeEntityStatus.getKey()),
        "reindexStatus", mergeEntityStatus.getValue().getValue());
    }

    var uploadInProgress = entityTypes.stream()
      .filter(statusesByType::containsKey)
      .filter(entityType -> statusesByType.get(entityType) == ReindexStatus.UPLOAD_IN_PROGRESS)
      .findAny();
    if (uploadInProgress.isPresent()) {
      throw new RequestValidationException(
        "Reindex Upload in Progress", "entityType", uploadInProgress.get().getType()
      );
    }
  }

  private void validateTenant(String operation, String tenantId) {
    var central = consortiumService.getCentralTenant(tenantId);
    if (central.isPresent() && !central.get().equals(tenantId)) {
      log.info("{}:: could not be started for consortium member tenant [tenantId: {}]", operation, tenantId);
      throw RequestValidationException.memberTenantNotAllowedException(tenantId);
    }
  }
}
