package org.folio.search.service.reindex;

import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;
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
import org.springframework.cache.annotation.CacheEvict;
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

  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> submitFullReindex(String tenantId, IndexSettings indexSettings) {
    return submitFullReindex(tenantId, indexSettings, null);
  }

  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> submitFullReindex(String tenantId, IndexSettings indexSettings, 
                                                   String targetTenantId) {
    log.info("submitFullReindex:: for [requestingTenant: {}, targetTenant: {}]", 
      tenantId, targetTenantId != null ? targetTenantId : "all consortium members");

    validateTenant("submitFullReindex", tenantId);

    reindexCommonService.deleteAllRecords(targetTenantId);
    statusService.recreateMergeStatusRecords(targetTenantId);
    
    // For tenant-specific reindex, check if indexes exist and create them if needed
    // This preserves existing indexes with shared documents while ensuring missing indexes are created
    if (targetTenantId == null) {
      // Full reindex - recreate all indexes (existing behavior)
      recreateIndices(tenantId, ReindexEntityType.supportUploadTypes(), indexSettings);
      log.info("submitFullReindex:: recreated indexes for full reindex [requestingTenant: {}]", tenantId);
    } else {
      // Tenant-specific reindex - ensure indexes exist without recreating existing ones
      ensureIndicesExist(tenantId, ReindexEntityType.supportUploadTypes(), indexSettings);
      log.info("submitFullReindex:: ensured indexes exist for tenant-specific reindex "
        + "[requestingTenant: {}, targetTenant: {}]", tenantId, targetTenantId);
    }

    // Capture context before async execution
    final String memberTenantIdContext = targetTenantId;
    
    var future = CompletableFuture.runAsync(() -> {
      try {
        // Restore context in executor thread
        if (memberTenantIdContext != null) {
          ReindexContext.setMemberTenantId(memberTenantIdContext);
          ReindexContext.setReindexMode(true); // Enable staging
        }
        
        mergeRangeService.truncateMergeRanges();
        
        List<MergeRangeEntity> rangesForAllTenants;
        if (memberTenantIdContext != null) {
          // Only process the member tenant (no central tenant in merge)
          rangesForAllTenants = processForConsortium(tenantId, memberTenantIdContext);
        } else {
          // Full consortium reindex: Process central + all members (no staging)
          rangesForAllTenants = Stream.of(
              mergeRangeService.createMergeRanges(tenantId),  // Central
              processForConsortium(tenantId, null)             // Members
            )
            .flatMap(List::stream)
            .toList();
        }
        
        mergeRangeService.saveMergeRanges(rangesForAllTenants);
      } finally {
        // Clean up context in executor thread
        if (memberTenantIdContext != null) {
          ReindexContext.clearMemberTenantId();
          ReindexContext.setReindexMode(false);
        }
      }
    }, reindexFullExecutor)
      .thenRun(() -> {
        // Restore context before publishing
        if (memberTenantIdContext != null) {
          ReindexContext.setMemberTenantId(memberTenantIdContext);
        }
        try {
          publishRecordsRange(tenantId, memberTenantIdContext);
        } finally {
          if (memberTenantIdContext != null) {
            ReindexContext.clearMemberTenantId();
          }
        }
      })
      .handle((unused, throwable) -> {
        if (throwable != null) {
          log.error("initFullReindex:: process failed [tenantId: {}, targetTenant: {}, error: {}]", 
            tenantId, targetTenantId, throwable);
          statusService.updateReindexMergeFailed();
        }
        return unused;
      });

    log.info("submitFullReindex:: submitted [requestingTenant: {}, targetTenant: {}]", 
      tenantId, targetTenantId != null ? targetTenantId : "all consortium members");
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

    // Capture context before async execution
    final String memberTenantIdContext = ReindexContext.getMemberTenantId();

    var futures = new ArrayList<>();
    for (var entityType : entityTypes) {
      var future = CompletableFuture.runAsync(() -> {
        // Restore context in executor thread
        if (memberTenantIdContext != null) {
          ReindexContext.setMemberTenantId(memberTenantIdContext);
        }
        try {
          uploadRangeService.prepareAndSendIndexRanges(entityType);
        } finally {
          // Clean up context in executor thread
          if (memberTenantIdContext != null) {
            ReindexContext.clearMemberTenantId();
          }
        }
      }, reindexUploadExecutor)
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

  /**
   * Submits upload reindex with tenant-specific document cleanup for member tenant reindex operations.
   * This method performs OpenSearch document cleanup for the specified tenant while preserving
   * shared consortium instances, then proceeds with upload processing using the member tenant context.
   *
   * @param tenantId the requesting tenant ID
   * @param entityTypes the entity types to reindex
   * @param targetTenantId the specific tenant whose documents should be cleaned up (null for full cleanup)
   * @return CompletableFuture representing the upload operation
   */
  public CompletableFuture<Void> submitUploadReindexWithTenantCleanup(String tenantId,
                                                                     List<ReindexEntityType> entityTypes,
                                                                     String targetTenantId) {
    log.info("submitUploadReindexWithTenantCleanup:: for [tenantId: {}, entities: {}, targetTenant: {}]",
      tenantId, entityTypes, targetTenantId);

    // Perform tenant-specific document cleanup before upload processing
    if (targetTenantId != null) {
      log.info("submitUploadReindexWithTenantCleanup:: performing OpenSearch document cleanup "
        + "for tenant [{}] while preserving shared instances", targetTenantId);
      try {
        reindexCommonService.deleteIndexDocumentsByTenantId(targetTenantId, true);
        log.info("submitUploadReindexWithTenantCleanup:: completed OpenSearch cleanup for tenant [{}]", 
          targetTenantId);
      } catch (Exception e) {
        log.error("submitUploadReindexWithTenantCleanup:: failed to cleanup OpenSearch documents "
          + "for tenant [{}]: {}", targetTenantId, e.getMessage(), e);
        throw new RuntimeException("Failed to cleanup tenant documents before upload", e);
      }
    }

    // Set member tenant context for upload phase
    if (targetTenantId != null) {
      ReindexContext.setMemberTenantId(targetTenantId);
    }
    
    try {
      // Standard upload processing - context determines data fetching
      return submitUploadReindex(tenantId, entityTypes, false, null);
    } finally {
      if (targetTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }
  }

  public CompletableFuture<Void> submitFailedMergeRangesReindex(String tenantId) {
    log.info("submitFailedMergeRangesReindex:: for [tenantId: {}]", tenantId);

    validateTenant("submitFailedMergeRangesReindex", tenantId);

    var failedRanges = mergeRangeService.fetchFailedMergeRanges();
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

  private void ensureIndicesExist(String tenantId, List<ReindexEntityType> entityTypes, IndexSettings indexSettings) {
    for (var reindexEntityType : entityTypes) {
      reindexCommonService.ensureIndexExists(reindexEntityType, tenantId, indexSettings);
    }
  }

  private List<MergeRangeEntity> processForConsortium(String tenantId, String targetTenantId) {
    List<MergeRangeEntity> mergeRangeEntities = new ArrayList<>();
    
    if (targetTenantId != null) {
      // Member tenant reindex: Process ONLY the member tenant data
      // Central tenant data will be handled in upload phase
      log.info("processForConsortium:: member tenant reindex - processing only [{}]", targetTenantId);
      
      // Set context to indicate member tenant reindex (enables staging)
      ReindexContext.setMemberTenantId(targetTenantId);
      
      // Process ONLY the target member tenant
      mergeRangeEntities.addAll(
        executionService.executeSystemUserScoped(targetTenantId, 
          () -> mergeRangeService.createMergeRanges(targetTenantId))
      );
      
      // DO NOT process central tenant here - it will be handled in upload phase
    } else {
      // Full consortium reindex: Process all member tenants (existing logic)
      var memberTenants = consortiumService.getConsortiumTenants(tenantId);
      for (var memberTenant : memberTenants) {
        log.info("processForConsortium:: processing member tenant [{}]", memberTenant);
        mergeRangeEntities.addAll(
          executionService.executeSystemUserScoped(memberTenant, 
            () -> mergeRangeService.createMergeRanges(memberTenant))
        );
      }
    }
    
    return mergeRangeEntities;
  }

  private void publishRecordsRange(String tenantId) {
    publishRecordsRange(tenantId, null);
  }
  
  private void publishRecordsRange(String tenantId, String targetTenantId) {
    // Capture context before async execution
    final String memberTenantIdContext = ReindexContext.getMemberTenantId();
    
    var futures = new ArrayList<>();
    for (var entityType : ReindexEntityType.supportMergeTypes()) {
      var rangeEntities = mergeRangeService.fetchMergeRanges(entityType);
      if (CollectionUtils.isNotEmpty(rangeEntities)) {
        // Filter ranges by target tenant if specified
        var filteredRanges = rangeEntities;
        if (targetTenantId != null) {
          filteredRanges = rangeEntities.stream()
            .filter(rangeEntity -> targetTenantId.equals(rangeEntity.getTenantId()))
            .toList();
          log.info("publishRecordsRange:: filtered ranges for target tenant [originalCount: {}, filteredCount: {}]",
            rangeEntities.size(), filteredRanges.size());
        }
        
        if (CollectionUtils.isNotEmpty(filteredRanges)) {
          log.info("publishRecordsRange:: publishing merge ranges "
            + "[requestingTenant: {}, entityType: {}, count: {}, targetTenant: {}]",
            tenantId, entityType, filteredRanges.size(), targetTenantId != null ? targetTenantId : "all");

          statusService.updateReindexMergeStarted(entityType, filteredRanges.size());
          for (var rangeEntity : filteredRanges) {
            var publishFuture = CompletableFuture.runAsync(() -> {
              // Restore context in executor thread
              if (memberTenantIdContext != null) {
                ReindexContext.setMemberTenantId(memberTenantIdContext);
              }
              try {
                executionService.executeSystemUserScoped(rangeEntity.getTenantId(), () -> {
                  inventoryService.publishReindexRecordsRange(rangeEntity);
                  return null;
                });
              } finally {
                // Clean up context in executor thread
                if (memberTenantIdContext != null) {
                  ReindexContext.clearMemberTenantId();
                }
              }
            }, reindexPublisherExecutor);
            futures.add(publishFuture);
          }
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
