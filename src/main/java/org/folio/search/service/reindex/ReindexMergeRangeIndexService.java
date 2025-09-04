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
import org.folio.search.model.event.ReindexRecordsEvent;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ReindexRangeStatus;
import org.folio.search.service.InstanceChildrenResourceService;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class ReindexMergeRangeIndexService {

  private static final int STATS_LOG_INTERVAL = 100; // Log stats every 100 merge ranges

  private final Map<ReindexEntityType, MergeRangeRepository> repositories;
  private final InventoryService inventoryService;
  private final ReindexConfigurationProperties reindexConfig;
  private final StagingDeduplicationService deduplicationService;
  private int mergeRangeCounter = 0;

  private InstanceChildrenResourceService instanceChildrenResourceService;

  public ReindexMergeRangeIndexService(List<MergeRangeRepository> repositories,
                                       InventoryService inventoryService,
                                       ReindexConfigurationProperties reindexConfig,
                                       @Autowired(required = false) StagingDeduplicationService deduplicationService) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(MergeRangeRepository::entityType, Function.identity()));
    this.inventoryService = inventoryService;
    this.reindexConfig = reindexConfig;
    this.deduplicationService = deduplicationService;
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

  public List<MergeRangeEntity> createMergeRanges(String tenantId) {
    List<MergeRangeEntity> mergeRangeEntities = new ArrayList<>();
    var rangeSize = reindexConfig.getMergeRangeSize();
    for (var recordType : InventoryRecordType.values()) {
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

  public List<MergeRangeEntity> fetchMergeRanges(ReindexEntityType entityType) {
    return repositories.get(entityType).getMergeRanges();
  }

  public List<MergeRangeEntity> fetchFailedMergeRanges() {
    return repositories.values().iterator().next().getFailedMergeRanges();
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

    // Periodically log staging table stats
    mergeRangeCounter++;
    if (mergeRangeCounter % STATS_LOG_INTERVAL == 0) {
      var stats = getStagingTableStats();
      log.info("Staging table stats after {} merge ranges: {}", mergeRangeCounter, stats);
    }
  }

  private List<MergeRangeEntity> constructMergeRangeRecords(int recordsCount,
                                                            int rangeSize,
                                                            InventoryRecordType recordType,
                                                            String tenantId) {
    log.info("constructMergeRangeRecords:: [tenantId: {}, recordType: {}, recordsCount: {}, rangeSize: {}]",
      tenantId, recordType, recordsCount, rangeSize);
    if (recordsCount == 0) {
      log.info("constructMergeRangeRecords:: constructed empty range [tenantId: {}, entityType: {}]",
        tenantId, recordType);
      var range = RangeGenerator.emptyUuidRange();
      var mergeRangeEntity = mergeEntity(recordType, tenantId, range.lowerBound(), range.upperBound());
      return List.of(mergeRangeEntity);
    }
    var rangesCount = (int) Math.ceil((double) recordsCount / rangeSize);
    return RangeGenerator.createUuidRanges(rangesCount).stream()
      .map(range -> mergeEntity(recordType, tenantId, range.lowerBound(), range.upperBound()))
      .toList();
  }

  private MergeRangeEntity mergeEntity(InventoryRecordType recordType, String tenantId, String lowerId,
                                       String upperId) {
    return mergeEntity(UUID.randomUUID(), recordType, tenantId, lowerId, upperId, Timestamp.from(Instant.now()));
  }

  private MergeRangeEntity mergeEntity(UUID id, InventoryRecordType recordType, String tenantId, String lowerId,
                                       String upperId, Timestamp createdAt) {
    return new MergeRangeEntity(id, asEntityType(recordType), tenantId, lowerId, upperId, createdAt, null, null);
  }

  private ReindexEntityType asEntityType(InventoryRecordType recordType) {
    if (recordType == InventoryRecordType.INSTANCE) {
      return ReindexEntityType.INSTANCE;
    } else if (recordType == InventoryRecordType.HOLDING) {
      return ReindexEntityType.HOLDINGS;
    } else {
      return ReindexEntityType.ITEM;
    }
  }

  public void performDeduplication() {
    performDeduplication(null);
  }

  public void performDeduplication(String targetTenantId) {
    if (deduplicationService != null) {
      // Log staging table stats before deduplication
      var statsBeforeDedup = getStagingTableStats();
      log.info("Staging table stats before deduplication: {}", statsBeforeDedup);

      if (targetTenantId != null) {
        log.info("Starting tenant-specific deduplication of staging tables for tenant: {}", targetTenantId);
        var result = deduplicationService.deduplicateAllStagingTables(targetTenantId);
        log.info("Tenant-specific deduplication completed for {}: instances={}, holdings={}, "
            + "items={}, relationships={}",
          targetTenantId, result.getTotalInstances(), result.getTotalHoldings(),
          result.getTotalItems(), result.getTotalRelationships());
      } else {
        log.info("Starting full deduplication of staging tables");
        var result = deduplicationService.deduplicateAllStagingTables();
        log.info("Full deduplication completed successfully: instances={}, holdings={}, items={}, relationships={}",
          result.getTotalInstances(), result.getTotalHoldings(),
          result.getTotalItems(), result.getTotalRelationships());
      }

      // Log staging table stats after deduplication (should be empty)
      var statsAfterDedup = getStagingTableStats();
      log.info("Staging table stats after deduplication: {}", statsAfterDedup);
    } else {
      log.debug("Deduplication service not available");
    }
  }

  public Map<String, Long> getStagingTableStats() {
    if (deduplicationService != null) {
      return deduplicationService.getStagingTableStats();
    }
    return Map.of();
  }
}
