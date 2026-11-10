package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.RESOURCE_NAME_MAP;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.model.event.ReindexRecordType;
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.ItemIndexService;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.RawLine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexMergeRangeIndexService {

  private final Map<ReindexEntityType, MergeRangeRepository> repositories;
  private final InventoryService inventoryService;
  private final ReindexConfigurationProperties reindexConfig;
  private final StagingMigrationService migrationService;
  private final ReindexStatusService statusService;
  private final ObjectProvider<ItemIndexService> itemIndexServiceProvider;

  private InstanceChildrenResourceService instanceChildrenResourceService;

  public ReindexMergeRangeIndexService(List<MergeRangeRepository> repositories,
                                       InventoryService inventoryService,
                                       ReindexConfigurationProperties reindexConfig,
                                       StagingMigrationService migrationService,
                                       ReindexStatusService statusService,
                                       ObjectProvider<ItemIndexService> itemIndexServiceProvider) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(MergeRangeRepository::entityType, Function.identity()));
    this.inventoryService = inventoryService;
    this.reindexConfig = reindexConfig;
    this.migrationService = migrationService;
    this.statusService = statusService;
    this.itemIndexServiceProvider = itemIndexServiceProvider;
    this.instanceChildrenResourceService = null;
  }

  @Autowired(required = false)
  public void setInstanceChildrenResourceService(InstanceChildrenResourceService instanceChildrenResourceService) {
    this.instanceChildrenResourceService = instanceChildrenResourceService;
  }

  public void saveMergeRanges(List<MergeRangeEntity> ranges) {
    repositories.values().iterator().next().saveMergeRanges(ranges);
  }

  public void truncateMergeRanges() {
    repositories.values().iterator().next().truncateMergeRanges();
  }

  /**
   * Deletes merge ranges of the given entity type only, ranges of other entity types are kept.
   */
  public void deleteMergeRanges(ReindexEntityType entityType) {
    repositories.get(entityType).deleteMergeRanges();
  }

  public List<MergeRangeEntity> createMergeRanges(String tenantId) {
    return createMergeRanges(tenantId, List.of(InventoryRecordType.values()));
  }

  private List<MergeRangeEntity> createMergeRanges(String tenantId, List<InventoryRecordType> recordTypes) {
    List<MergeRangeEntity> mergeRangeEntities = new ArrayList<>();
    var rangeSize = reindexConfig.getMergeRangeSize();
    for (var recordType : recordTypes) {
      var recordsCount = inventoryService.fetchInventoryRecordsCount(recordType);
      var ranges = constructMergeRangeRecords(recordsCount, rangeSize, recordType, tenantId);
      if (CollectionUtils.isNotEmpty(ranges)) {
        log.info("createMergeRanges:: constructed [tenantId: {}, entityType: {}, count: {}]",
          tenantId, recordType, ranges.size());
        mergeRangeEntities.addAll(ranges);
      }
    }
    return mergeRangeEntities;
  }

  /**
   * Creates merge ranges for item records only, used by the items-only reindex.
   */
  public List<MergeRangeEntity> createItemMergeRanges(String tenantId) {
    return createMergeRanges(tenantId, List.of(InventoryRecordType.ITEM));
  }

  public List<MergeRangeEntity> fetchMergeRanges(ReindexEntityType entityType) {
    return repositories.get(entityType).getMergeRanges();
  }

  public List<MergeRangeEntity> fetchFailedMergeRanges() {
    return repositories.values().iterator().next().getFailedMergeRanges();
  }

  public List<MergeRangeEntity> fetchFailedMergeRanges(ReindexEntityType entityType) {
    return repositories.get(entityType).getFailedMergeRangesOfEntityType();
  }

  /**
   * Checks if merge ranges of the given entity type are still being processed.
   *
   * @param entityType  entity type of the ranges
   * @param activeSince ranges with no activity after this time are considered abandoned
   * @return true if the ranges are still being processed
   */
  public boolean hasActiveMergeRanges(ReindexEntityType entityType, Timestamp activeSince) {
    return repositories.get(entityType).hasActiveMergeRanges(activeSince);
  }

  public void updateStatus(ReindexEntityType entityType, String rangeId, ReindexRangeStatus status, String failCause) {
    var repository = repositories.get(entityType);
    repository.updateRangeStatus(UUID.fromString(rangeId), Timestamp.from(Instant.now()), status, failCause);
  }

  @SuppressWarnings("unchecked")
  public void saveEntities(ReindexRecordsEvent event) {
    var entities = event.getRecords().stream()
      .map(entity -> (Map<String, Object>) entity)
      .toList();

    if (indexItemsOnly(event.getRecordType(), event.getTenant(), entities)) {
      return;
    }

    try {
      // Set reindex mode for context-aware repositories
      ReindexContext.setReindexMode(true);

      // Use unified repository which will route to staging based on context
      var repository = repositories.get(event.getRecordType().getEntityType());
      repository.saveEntities(event.getTenant(), entities);

      if (instanceChildrenResourceService != null) {
        instanceChildrenResourceService.persistChildrenOnReindex(event.getTenant(),
          RESOURCE_NAME_MAP.get(event.getRecordType().getEntityType()), entities);
      }
    } finally {
      // Only clear reindex mode, preserve member tenant context for outer scope
      ReindexContext.setReindexMode(false);
    }
  }

  public void saveEntitiesRaw(String tenant, ReindexRecordType recordType, List<RawLine> entities) {
    var maps = entities.stream().map(RawLine::data).toList();
    if (indexItemsOnly(recordType, tenant, maps)) {
      return;
    }

    try {
      ReindexContext.setReindexMode(true);
      repositories.get(recordType.getEntityType()).saveEntitiesRaw(tenant, entities);
      if (instanceChildrenResourceService != null) {
        instanceChildrenResourceService.persistChildrenOnReindex(
          tenant, RESOURCE_NAME_MAP.get(recordType.getEntityType()), maps);
      }
    } finally {
      ReindexContext.setReindexMode(false);
    }
  }

  /**
   * Writes item records straight to the item index when an items-only reindex is running. Neither the item table nor
   * the instance children are touched in that case.
   *
   * @return true if the records were indexed and the regular save must be skipped
   */
  private boolean indexItemsOnly(ReindexRecordType recordType, String tenant, List<Map<String, Object>> records) {
    if (recordType != ReindexRecordType.ITEM || !statusService.isItemOnlyReindex()) {
      return false;
    }
    var itemIndexService = itemIndexServiceProvider.getIfAvailable();
    if (itemIndexService == null) {
      throw new IllegalStateException("Items-only reindex requires ITEM_INDEX_ENABLED=true");
    }
    itemIndexService.reindexItems(tenant, records);
    return true;
  }

  private List<MergeRangeEntity> constructMergeRangeRecords(int recordsCount,
                                                            int rangeSize,
                                                            InventoryRecordType recordType,
                                                            String tenantId) {
    var traceId = UUID.randomUUID();
    log.info("constructMergeRangeRecords:: [tenantId: {}, traceId: {}, "
             + "recordType: {}, recordsCount: {}, rangeSize: {}]",
      tenantId, traceId, recordType, recordsCount, rangeSize);
    if (recordsCount == 0) {
      log.info("constructMergeRangeRecords:: constructed empty range [tenantId: {}, traceId: {}, entityType: {}]",
        tenantId, traceId, recordType);
      var range = RangeGenerator.emptyUuidRange();
      var mergeRangeEntity = mergeEntity(recordType, tenantId, traceId, range.lowerBound(), range.upperBound());
      return List.of(mergeRangeEntity);
    }
    var rangesCount = (int) Math.ceil((double) recordsCount / rangeSize);
    return RangeGenerator.createUuidRanges(rangesCount).stream()
      .map(range -> mergeEntity(recordType, tenantId, traceId, range.lowerBound(), range.upperBound()))
      .toList();
  }

  private MergeRangeEntity mergeEntity(InventoryRecordType recordType, String tenantId, UUID traceId, String lowerId,
                                       String upperId) {
    return mergeEntity(UUID.randomUUID(), recordType, tenantId, traceId, lowerId, upperId,
      Timestamp.from(Instant.now()));
  }

  private MergeRangeEntity mergeEntity(UUID id, InventoryRecordType recordType, String tenantId, UUID traceId,
                                       String lowerId, String upperId, Timestamp createdAt) {
    return new MergeRangeEntity(id, traceId, asEntityType(recordType), tenantId, lowerId, upperId,
      createdAt, null, null);
  }

  private ReindexEntityType asEntityType(InventoryRecordType recordType) {
    return switch (recordType) {
      case InventoryRecordType.INSTANCE -> ReindexEntityType.INSTANCE;
      case InventoryRecordType.HOLDING -> ReindexEntityType.HOLDINGS;
      default -> ReindexEntityType.ITEM;
    };
  }

  public void performStagingMigration(String targetTenantId) {
    log.info("performStagingMigration:: Starting tenant-specific migration of staging tables for tenant: {}",
      targetTenantId);
    var result = migrationService.migrateAllStagingTables(targetTenantId);
    log.info("performStagingMigration:: Tenant-specific migration completed for {}: instances={}, holdings={}, "
        + "items={}, relationships={}",
      targetTenantId, result.getTotalInstances(), result.getTotalHoldings(),
      result.getTotalItems(), result.getTotalRelationships());
  }
}
