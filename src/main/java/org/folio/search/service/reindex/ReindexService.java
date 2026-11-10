package org.folio.search.service.reindex;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;
import static org.folio.search.model.types.ReindexStatus.MERGE_FAILED;
import static org.folio.search.model.types.ReindexStatus.MERGE_IN_PROGRESS;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.converter.ReindexEntityTypeMapper;
import org.folio.search.domain.dto.IndexSettings;
import org.folio.search.domain.dto.ReindexUploadDto;
import org.folio.search.exception.ReindexException;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexStatus;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.ItemIndexService;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexService {

  private static final int RANGE_PUBLISH_PARTITION_SIZE = 2000;
  private static final Duration ITEM_RANGES_ACTIVITY_WINDOW = Duration.ofMinutes(10);

  private final ConsortiumTenantService consortiumService;
  private final EgressExecutionContextService executionService;
  private final ReindexMergeRangeIndexService mergeRangeService;
  private final ReindexUploadRangeIndexService uploadRangeService;
  private final ReindexStatusService statusService;
  private final InventoryService inventoryService;
  private final ExecutorService reindexFullExecutor;
  private final ExecutorService reindexUploadExecutor;
  private final ExecutorService reindexPublisherExecutor;
  private final ReindexEntityTypeMapper entityTypeMapper;
  private final ReindexCommonService reindexCommonService;
  private final ObjectProvider<ItemIndexService> itemIndexServiceProvider;

  public ReindexService(ConsortiumTenantService consortiumService, EgressExecutionContextService executionService,
                        ReindexMergeRangeIndexService mergeRangeService,
                        ReindexUploadRangeIndexService uploadRangeService, ReindexStatusService statusService,
                        InventoryService inventoryService,
                        @Qualifier("reindexFullExecutor") ExecutorService reindexFullExecutor,
                        @Qualifier("reindexUploadExecutor") ExecutorService reindexUploadExecutor,
                        @Qualifier("reindexPublisherExecutor") ExecutorService reindexPublisherExecutor,
                        ReindexEntityTypeMapper entityTypeMapper, ReindexCommonService reindexCommonService,
                        ObjectProvider<ItemIndexService> itemIndexServiceProvider) {
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
    this.itemIndexServiceProvider = itemIndexServiceProvider;
  }

  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> submitFullReindex(String tenantId, IndexSettings indexSettings) {
    return submitFullReindex(tenantId, indexSettings, null);
  }

  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> submitFullReindex(String tenantId, IndexSettings indexSettings,
                                                   String targetTenantId) {
    log.info("submitFullReindex:: for [requestingTenant: {}, targetTenant: {}]", tenantId,
      targetTenantId != null ? targetTenantId : "all consortium members");

    validateTenant("submitFullReindex", tenantId, targetTenantId);
    validateReindexNotInProgress();

    reindexCommonService.deleteAllRecords(targetTenantId);
    reindexCommonService.disableAutoVacuumEntityTables();
    statusService.recreateMergeStatusRecords(targetTenantId);

    recreateIndices(tenantId, targetTenantId, indexSettings);

    // Capture context before async execution
    final var memberTenantIdContext = targetTenantId;

    var future = CompletableFuture.runAsync(() -> recreateMergeRanges(tenantId, targetTenantId), reindexFullExecutor)
      .thenRun(() -> publishRecordsRange(tenantId, memberTenantIdContext))
      .handle(handleReindexingFailure(tenantId, targetTenantId));

    log.info("submitFullReindex:: submitted [requestingTenant: {}, targetTenant: {}]", tenantId,
      targetTenantId != null ? targetTenantId : "all consortium members");
    return future;
  }

  /**
   * Starts an items-only reindex: item records are pulled from inventory and written straight to the item index.
   * Neither the PostgreSQL tables, nor other indexes, nor the status rows of other entity types are touched.
   *
   * @param tenantId      the requesting tenant ID
   * @param indexSettings settings of the recreated item index, optional
   * @return future that completes when merge ranges are published to inventory
   * @throws ReindexException if the item index can't be recreated
   */
  @CacheEvict(cacheNames = USER_TENANTS_CACHE, allEntries = true, beforeInvocation = true)
  public CompletableFuture<Void> submitItemReindex(String tenantId, IndexSettings indexSettings) {
    log.info("submitItemReindex:: for [requestingTenant: {}]", tenantId);

    validateTenant("submitItemReindex", tenantId);
    if (itemIndexServiceProvider.getIfAvailable() == null) {
      throw new RequestValidationException("Item index is not enabled, items-only reindex is not available",
        "itemIndexEnabled", "false");
    }
    validateItemReindexAllowed();

    statusService.startItemOnlyReindex();
    recreateItemIndex(tenantId, indexSettings);

    var future = CompletableFuture.runAsync(() -> recreateItemMergeRanges(tenantId), reindexFullExecutor)
      .thenRun(() -> publishItemRecordsRange(tenantId))
      .handle(handleItemReindexingFailure(tenantId));

    log.info("submitItemReindex:: submitted [requestingTenant: {}]", tenantId);
    return future;
  }

  /**
   * Republishes failed ITEM merge ranges of an items-only reindex. Failed ranges of other entity types are ignored.
   */
  public CompletableFuture<Void> submitFailedItemRangesReindex(String tenantId) {
    log.info("submitFailedItemRangesReindex:: for [tenantId: {}]", tenantId);

    validateTenant("submitFailedItemRangesReindex", tenantId);

    var failedRanges = mergeRangeService.fetchFailedMergeRanges(ReindexEntityType.ITEM);
    if (CollectionUtils.isEmpty(failedRanges)) {
      log.info("submitFailedItemRangesReindex:: no failed ranges found");
      return CompletableFuture.completedFuture(null);
    }

    log.info("submitFailedItemRangesReindex:: for [tenantId: {}, count: {}]", tenantId, failedRanges.size());
    statusService.updateReindexMergeInProgress(Set.of(ReindexEntityType.ITEM));

    var futures = failedRanges.stream()
      .map(rangeEntity -> CompletableFuture.runAsync(() -> executionService.execute(rangeEntity.getTenantId(), () -> {
        inventoryService.publishReindexRecordsRange(rangeEntity);
        return null;
      }), reindexPublisherExecutor))
      .toArray(CompletableFuture[]::new);

    return CompletableFuture.allOf(futures);
  }

  /**
   * Records are only created in the item index, so a run over an index that wasn't recreated would skip every existing
   * document and still report success. The run has to fail instead.
   */
  private void recreateItemIndex(String tenantId, IndexSettings indexSettings) {
    try {
      reindexCommonService.recreateIndexOrThrow(ReindexEntityType.ITEM, tenantId, indexSettings);
    } catch (ReindexException e) {
      statusService.updateReindexMergeFailed(ReindexEntityType.ITEM);
      throw e;
    }
  }

  private void recreateItemMergeRanges(String tenantId) {
    mergeRangeService.deleteMergeRanges(ReindexEntityType.ITEM);

    var ranges = new ArrayList<>(mergeRangeService.createItemMergeRanges(tenantId));
    for (var memberTenant : consortiumService.getConsortiumTenants(tenantId)) {
      log.info("recreateItemMergeRanges:: processing member tenant [{}]", memberTenant);
      ranges.addAll(executionService.execute(memberTenant,
        () -> mergeRangeService.createItemMergeRanges(memberTenant)));
    }
    mergeRangeService.saveMergeRanges(ranges);
  }

  private void publishItemRecordsRange(String tenantId) {
    CompletableFuture
      .supplyAsync(() -> fetchAndPreparePartitions(tenantId, null, ReindexEntityType.ITEM), reindexPublisherExecutor)
      .thenCompose(partitions -> publishPartitionsInParallel(partitions, null))
      .join();
  }

  private BiFunction<Void, Throwable, Void> handleItemReindexingFailure(String tenantId) {
    return (unused, throwable) -> {
      if (throwable != null) {
        log.error("submitItemReindex:: process failed [tenantId: {}, error: {}]", tenantId, throwable);
        // only the ITEM row: the no-arg overload would mark every merge type failed
        statusService.updateReindexMergeFailed(ReindexEntityType.ITEM);
      }
      return unused;
    };
  }

  private void validateItemReindexAllowed() {
    var statusesByType = statusService.getStatusesByType();
    var itemOnlyRun = statusService.isItemOnlyReindex();
    for (var entry : statusesByType.entrySet()) {
      var type = entry.getKey();
      var status = entry.getValue();
      var previousItemOnlyFailure = type == ReindexEntityType.ITEM && status == MERGE_FAILED && itemOnlyRun;
      if (previousItemOnlyFailure) {
        validateNoActiveItemRanges();
      } else if (blocksItemReindex(type, status)) {
        throw new RequestValidationException(
          "Items-only reindex can't be started, reindex is in progress or failed for: %s".formatted(type),
          "reindexStatus", status.getValue());
      }
    }
  }

  /**
   * MERGE_COMPLETED and STAGING_COMPLETED are transient only for entity types with an upload phase. For merge-only
   * types they are final and must not block.
   */
  private static boolean blocksItemReindex(ReindexEntityType type, ReindexStatus status) {
    return switch (status) {
      case MERGE_IN_PROGRESS, UPLOAD_IN_PROGRESS, STAGING_IN_PROGRESS, MERGE_FAILED, UPLOAD_FAILED, STAGING_FAILED ->
        true;
      case MERGE_COMPLETED, STAGING_COMPLETED -> type.isSupportsUpload();
      default -> false;
    };
  }

  /**
   * A range fails the ITEM row while other ranges of the run are still in flight. Restarting then would let their older
   * records into the new index, so a restart waits until the previous run has gone quiet.
   */
  private void validateNoActiveItemRanges() {
    var activeSince = Timestamp.from(Instant.now().minus(ITEM_RANGES_ACTIVITY_WINDOW));
    if (mergeRangeService.hasActiveMergeRanges(ReindexEntityType.ITEM, activeSince)) {
      throw new RequestValidationException(
        "Items-only reindex can't be restarted, ranges of the previous run are still being processed",
        "reindexStatus", MERGE_FAILED.getValue());
    }
  }

  private void recreateMergeRanges(String tenantId, String memberTenantIdContext) {
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
        rangesForAllTenants = Stream.of(mergeRangeService.createMergeRanges(tenantId),  // Central
          processForConsortium(tenantId, null)             // Members
        ).flatMap(List::stream).toList();
      }

      mergeRangeService.saveMergeRanges(rangesForAllTenants);
    } finally {
      // Clean up context in executor thread
      if (memberTenantIdContext != null) {
        ReindexContext.clearMemberTenantId();
        ReindexContext.setReindexMode(false);
      }
    }
  }

  public CompletableFuture<Void> submitUploadReindex(String tenantId, ReindexUploadDto reindexUploadDto) {
    var entityTypes = entityTypeMapper.convert(reindexUploadDto.getEntityTypes()).stream()
      .filter(ReindexEntityType::isSupportsUpload)
      .toList();
    return submitUploadReindex(tenantId, entityTypes, true, reindexUploadDto.getIndexSettings());
  }

  public CompletableFuture<Void> submitUploadReindex(String tenantId, List<ReindexEntityType> entityTypes) {
    return submitUploadReindex(tenantId, entityTypes, false, null);
  }

  private CompletableFuture<Void> submitUploadReindex(String tenantId, List<ReindexEntityType> entityTypes,
                                                      boolean recreateIndex, IndexSettings indexSettings) {
    log.info("submitUploadReindex:: for [tenantId: {}, entities: {}]", tenantId, entityTypes);

    validateUploadReindex(tenantId, entityTypes);

    // Capture context before async execution
    final var memberTenantIdContext = ReindexContext.getMemberTenantId();
    prepareForUploadReindex(tenantId, memberTenantIdContext, entityTypes, recreateIndex, indexSettings);

    var futures = new ArrayList<>();
    for (var entityType : entityTypes) {
      var future = CompletableFuture.runAsync(() ->
          sendIndexRanges(memberTenantIdContext, entityType), reindexUploadExecutor)
        .handle((unused, throwable) -> {
          if (throwable != null) {
            log.error("submitUploadReindex:: reindex upload process failed: {}", throwable.getMessage());
            statusService.updateReindexUploadFailed(entityType);
          }
          return unused;
        });
      futures.add(future);
    }

    log.info("submitUploadReindex:: submitted [tenantId: {}]", tenantId);
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private void prepareForUploadReindex(String tenantId, String memberTenantId, List<ReindexEntityType> entityTypes,
                                      boolean recreateIndex, IndexSettings indexSettings) {
    var targetTenantId = statusService.getTargetTenantId();
    for (var reindexEntityType : entityTypes) {
      statusService.upsertUploadStatusRecord(reindexEntityType, targetTenantId);
      if (recreateIndex) {
        reindexCommonService.recreateIndex(reindexEntityType, tenantId, indexSettings);
      }
    }

    //Analyze if not member tenant reindex
    if (isBlank(memberTenantId)) {
      reindexCommonService.enableAutoVacuumEntityTables();
    }
  }

  private void sendIndexRanges(String memberTenantIdContext, ReindexEntityType entityType) {
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
  }

  /**
   * Submits upload reindex with tenant-specific document cleanup for member tenant reindex operations.
   * This method performs OpenSearch document cleanup for the specified tenant while preserving
   * shared consortium instances, then proceeds with upload processing using the member tenant context.
   *
   * @param tenantId       the requesting tenant ID
   * @param entityTypes    the entity types to reindex
   * @param targetTenantId the specific tenant whose documents should be cleaned up (null for full cleanup)
   * @return CompletableFuture representing the upload operation
   */
  public CompletableFuture<Void> submitUploadReindexWithTenantCleanup(String tenantId,
                                                                      List<ReindexEntityType> entityTypes,
                                                                      String targetTenantId) {
    log.info("submitUploadReindexWithTenantCleanup:: for [tenantId: {}, entities: {}, targetTenant: {}]", tenantId,
      entityTypes, targetTenantId);

    // Perform tenant-specific document cleanup before upload processing
    performTenantCleanup(targetTenantId);

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

  private void performTenantCleanup(String targetTenantId) {
    if (targetTenantId != null) {
      log.info("submitUploadReindexWithTenantCleanup:: performing OpenSearch document cleanup "
        + "for tenant [{}] while preserving shared instances", targetTenantId);
      try {
        reindexCommonService.deleteInstanceDocumentsByTenantId(targetTenantId);
        log.info("submitUploadReindexWithTenantCleanup:: completed OpenSearch cleanup for tenant [{}]", targetTenantId);
      } catch (Exception e) {
        log.error(
          "submitUploadReindexWithTenantCleanup:: failed to cleanup OpenSearch documents " + "for tenant [{}]: {}",
          targetTenantId, e.getMessage(), e);
        throw new ReindexException("Failed to cleanup tenant documents before upload", e);
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
    var entityTypes = failedRanges.stream().map(MergeRangeEntity::getEntityType).collect(Collectors.toSet());
    statusService.updateReindexMergeInProgress(entityTypes);

    var futures = new ArrayList<>();
    for (var rangeEntity : failedRanges) {
      var future =
        CompletableFuture.runAsync(() -> executionService.execute(rangeEntity.getTenantId(), () -> {
          inventoryService.publishReindexRecordsRange(rangeEntity);
          return null;
        }), reindexPublisherExecutor);
      futures.add(future);
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  private BiFunction<Void, Throwable, Void> handleReindexingFailure(String tenantId, String targetTenantId) {
    return (unused, throwable) -> {
      if (throwable != null) {
        log.error("initFullReindex:: process failed [tenantId: {}, targetTenant: {}, error: {}]", tenantId,
          targetTenantId, throwable);
        statusService.updateReindexMergeFailed();
      }
      return unused;
    };
  }

  private void recreateIndices(String tenantId, String targetTenantId, IndexSettings indexSettings) {
    if (targetTenantId == null) {
      // Full reindex - recreate all indexes (existing behavior)
      for (var reindexEntityType : ReindexEntityType.supportUploadTypes()) {
        reindexCommonService.recreateIndex(reindexEntityType, tenantId, indexSettings);
      }
      log.info("submitFullReindex:: recreated indexes for full reindex [requestingTenant: {}]", tenantId);
    } else {
      // Tenant-specific reindex - ensure indexes exist without recreating existing ones
      for (var reindexEntityType : ReindexEntityType.supportUploadTypes()) {
        reindexCommonService.ensureIndexExists(reindexEntityType, tenantId, indexSettings);
      }
      log.info("submitFullReindex:: ensured indexes exist for tenant-specific reindex "
        + "[requestingTenant: {}, targetTenant: {}]", tenantId, targetTenantId);
    }
  }

  private List<MergeRangeEntity> processForConsortium(String tenantId, String targetTenantId) {
    var mergeRangeEntities = new ArrayList<MergeRangeEntity>();

    if (targetTenantId != null) {
      // Member tenant reindex: Process ONLY the member tenant data
      // Central tenant data will be handled in upload phase
      log.info("processForConsortium:: member tenant reindex - processing only [{}]", targetTenantId);

      // Set context to indicate member tenant reindex (enables staging)
      ReindexContext.setMemberTenantId(targetTenantId);

      // Process ONLY the target member tenant
      mergeRangeEntities.addAll(executionService.execute(targetTenantId,
        () -> mergeRangeService.createMergeRanges(targetTenantId)));

      // DO NOT process central tenant here - it will be handled in upload phase
    } else {
      // Full consortium reindex: Process all member tenants (existing logic)
      var memberTenants = consortiumService.getConsortiumTenants(tenantId);
      for (var memberTenant : memberTenants) {
        log.info("processForConsortium:: processing member tenant [{}]", memberTenant);
        mergeRangeEntities.addAll(executionService.execute(memberTenant,
          () -> mergeRangeService.createMergeRanges(memberTenant)));
      }
    }

    return mergeRangeEntities;
  }

  private void publishRecordsRange(String tenantId, String targetTenantId) {
    if (targetTenantId != null) {
      ReindexContext.setMemberTenantId(targetTenantId);
    }
    try {
      final String memberTenantIdContext = ReindexContext.getMemberTenantId();

      var perTypeFutures = ReindexEntityType.supportMergeTypes().stream()
        .map(entityType -> CompletableFuture
          .supplyAsync(() -> fetchAndPreparePartitions(tenantId, targetTenantId, entityType), reindexPublisherExecutor)
          .thenCompose(partitions -> publishPartitionsInParallel(partitions, memberTenantIdContext)))
        .toArray(CompletableFuture[]::new);

      CompletableFuture.allOf(perTypeFutures).join();
    } finally {
      if (targetTenantId != null) {
        ReindexContext.clearMemberTenantId();
      }
    }
  }

  private List<List<MergeRangeEntity>> fetchAndPreparePartitions(String tenantId, String targetTenantId,
                                                                 ReindexEntityType entityType) {
    var rangeEntities = mergeRangeService.fetchMergeRanges(entityType);
    if (CollectionUtils.isEmpty(rangeEntities)) {
      return List.of();
    }
    log.info("publishRecordsRange:: publishing merge ranges "
        + "[requestingTenant: {}, entityType: {}, count: {}, targetTenant: {}]", tenantId, entityType,
      rangeEntities.size(), targetTenantId != null ? targetTenantId : "all");
    statusService.updateReindexMergeStarted(entityType, rangeEntities.size());
    return ListUtils.partition(rangeEntities, RANGE_PUBLISH_PARTITION_SIZE);
  }

  private CompletableFuture<Void> publishPartitionsInParallel(List<List<MergeRangeEntity>> partitions,
                                                              String memberTenantIdContext) {
    var futures = partitions.stream()
      .map(partition -> CompletableFuture.runAsync(
        () -> publishRangePartition(partition, memberTenantIdContext), reindexPublisherExecutor))
      .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }

  private void publishRangePartition(List<MergeRangeEntity> partition, String memberTenantIdContext) {
    if (memberTenantIdContext != null) {
      ReindexContext.setMemberTenantId(memberTenantIdContext);
    }
    try {
      for (var rangeEntity : partition) {
        executionService.execute(rangeEntity.getTenantId(), () -> {
          inventoryService.publishReindexRecordsRange(rangeEntity);
          return null;
        });
      }
    } finally {
      if (memberTenantIdContext != null) {
        ReindexContext.clearMemberTenantId();
      }
    }
  }

  private void validateUploadReindex(String tenantId, List<ReindexEntityType> entityTypes) {
    validateTenant("submitUploadReindex", tenantId);

    var statusesByType = statusService.getStatusesByType();
    // call numbers are derived from items, the other entity types are independent of an items-only run
    var ignoreItemRow = !entityTypes.contains(ReindexEntityType.CALL_NUMBER) && statusService.isItemOnlyReindex();

    var mergeNotComplete = statusesByType.entrySet().stream()
      .filter(status -> ReindexEntityType.supportMergeTypes().contains(status.getKey()))
      .filter(status -> !(ignoreItemRow && status.getKey() == ReindexEntityType.ITEM))
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
      throw new RequestValidationException("Reindex Upload in Progress", "entityType",
        uploadInProgress.get().getType());
    }
  }

  private void validateReindexNotInProgress() {
    var statusesByType = statusService.getStatusesByType();
    var inProgress = statusesByType.entrySet().stream()
      .filter(status -> status.getValue() == MERGE_IN_PROGRESS || status.getValue() == ReindexStatus.UPLOAD_IN_PROGRESS)
      .findFirst();
    if (inProgress.isPresent()) {
      var entityStatus = inProgress.get();
      throw new RequestValidationException("Reindex is already in progress for: %s".formatted(entityStatus.getKey()),
        "reindexStatus", entityStatus.getValue().getValue());
    }
  }

  private void validateTenant(String operation, String tenantId) {
    validateTenant(operation, tenantId, null);
  }

  private void validateTenant(String operation, String tenantId, String targetTenantId) {
    var central = consortiumService.getCentralTenant(tenantId);
    if (central.isPresent() && !central.get().equals(tenantId)) {
      log.info("{}:: could not be started for consortium member tenant [tenantId: {}]", operation, tenantId);
      throw RequestValidationException.memberTenantNotAllowedException(tenantId);
    }

    if (isBlank(targetTenantId)) {
      return;
    }

    if (central.isEmpty() && isNotBlank(targetTenantId)) {
      log.info("{}:: could not be started for non-consortium tenant with target tenant specified [tenantId: {}, "
        + "targetTenantId: {}]", operation, tenantId, targetTenantId);
      throw RequestValidationException.targetTenantNotAllowedException(targetTenantId);
    }

    var consortiumMembers = consortiumService.getConsortiumTenants(tenantId);
    if (central.isPresent() && !consortiumMembers.contains(targetTenantId)) {
      log.info("{}:: could not be started for consortium central tenant with target tenant"
        + "not being a consortium member [tenantId: {}, targetTenantId: {}]", operation, tenantId, targetTenantId);
      throw RequestValidationException.targetTenantNotAllowedException(targetTenantId);
    }
  }
}
