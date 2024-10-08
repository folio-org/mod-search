package org.folio.search.service.reindex;

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
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.springframework.stereotype.Service;

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

  public void updateFinishDate(ReindexEntityType entityType, String rangeId) {
    var repository = repositories.get(entityType);
    repository.setIndexRangeFinishDate(UUID.fromString(rangeId), Timestamp.from(Instant.now()));
  }

  @SuppressWarnings("unchecked")
  public void saveEntities(ReindexRecordsEvent event) {
    var entities = event.getRecords().stream()
      .map(entity -> (Map<String, Object>) entity)
      .toList();

    repositories.get(event.getRecordType().getEntityType()).saveEntities(event.getTenant(), entities);
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
    return new MergeRangeEntity(id, asEntityType(recordType), tenantId, lowerId, upperId, createdAt);
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
}
