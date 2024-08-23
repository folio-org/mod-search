package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

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
import org.folio.search.integration.InventoryService;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
public class ReindexMergeRangeIndexService {

  private final Map<ReindexEntityType, MergeRangeRepository> repositories;
  private final InventoryService inventoryService;
  private final ReindexConfigurationProperties reindexConfig;

  public ReindexMergeRangeIndexService(List<MergeRangeRepository> repositories,
                                       InventoryService inventoryService,
                                       ReindexConfigurationProperties reindexConfig) {
    this.repositories = repositories.stream()
      .collect(Collectors.toMap(MergeRangeRepository::entityType, Function.identity()));
    this.inventoryService = inventoryService;
    this.reindexConfig = reindexConfig;
  }

  @Transactional
  public void deleteAllRangeRecords() {
    MERGE_RANGE_ENTITY_TYPES.stream().map(repositories::get).forEach(MergeRangeRepository::truncate);
    repositories.get(ReindexEntityType.INSTANCE).truncateMergeRanges();
  }

  public void saveMergeRanges(List<MergeRangeEntity> ranges) {
    repositories.values().iterator().next().saveMergeRanges(ranges);
  }

  public List<MergeRangeEntity> createMergeRanges(String tenantId) {
    List<MergeRangeEntity> mergeRangeEntities = new ArrayList<>();
    var rangeSize = reindexConfig.getMergeRangeSize();
    for (var recordType : InventoryRecordType.values()) {
      var recordsCount = inventoryService.fetchInventoryRecordsCount(recordType);
      var ranges = constructMergeRangeRecords(recordsCount, rangeSize, recordType, tenantId);
      if (CollectionUtils.isNotEmpty(ranges)) {
        log.info("Constructed [{} {}] ranges for [tenant: {}]", ranges.size(), recordType, tenantId);
        mergeRangeEntities.addAll(ranges);
      }
    }
    return mergeRangeEntities;
  }

  public List<MergeRangeEntity> fetchMergeRanges(ReindexEntityType entityType) {
    return repositories.get(entityType).getMergeRanges();
  }

  private List<MergeRangeEntity> constructMergeRangeRecords(int recordsCount,
                                                            int rangeSize,
                                                            InventoryRecordType recordType,
                                                            String tenantId) {
    log.info("Constructing Merge Ranges: [recordType: {}, recordsCount: {}, tenant: {}]",
      recordType, recordsCount, tenantId);

    var rangesCount = (int) Math.ceil((double) recordsCount / rangeSize);
    return RangeGenerator.createRanges(rangesCount).stream()
      .map(range -> mergeEntity(UUID.randomUUID(), recordType, tenantId, range.lowerBound(), range.upperBound(),
        Timestamp.from(Instant.now())))
      .toList();
  }

  private MergeRangeEntity mergeEntity(UUID id, InventoryRecordType recordType, String tenantId, UUID lowerId,
                                       UUID upperId, Timestamp createdAt) {
    return new MergeRangeEntity(id, asEntityType(recordType), tenantId, lowerId, upperId, createdAt);
  }

  private ReindexEntityType asEntityType(InventoryRecordType recordType) {
    if (recordType == InventoryRecordType.INSTANCE) {
      return ReindexEntityType.INSTANCE;
    } else if (recordType == InventoryRecordType.HOLDING) {
      return ReindexEntityType.HOLDING;
    } else {
      return ReindexEntityType.ITEM;
    }
  }
}
