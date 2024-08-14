package org.folio.search.service.reindex;

import static org.folio.search.service.reindex.ReindexConstants.MERGE_RANGE_ENTITY_TYPES;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.integration.InventoryService;
import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.client.CqlQueryParam;
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
    repositories.get(ReindexEntityType.INSTANCE).truncateMergeRangeTable();
  }

  public void createMergeRanges(String tenantId) {
    var repository = repositories.get(ReindexEntityType.INSTANCE);
    for (var recordType : InventoryRecordType.values()) {
      var recordsCount = inventoryService.fetchInventoryRecordCount(recordType);
      var rangeSize = reindexConfig.getMergeRangeSize();
      var ranges = constructRecordMergeRanges(recordsCount, rangeSize, recordType, tenantId);

      log.info("Creating [{} {}] ranges for [tenant: {}]", ranges.size(), recordType, tenantId);
      repository.saveMergeRanges(ranges);
    }
  }

  public List<MergeRangeEntity> fetchMergeRanges(ReindexEntityType entityType) {
    return repositories.get(entityType).getMergeRanges();
  }

  public int fetchRangeEntitiesCount(ReindexEntityType entityType) {
    return Optional.ofNullable(repositories.get(entityType).countRangeEntities()).orElse(0);
  }

  private List<MergeRangeEntity> constructRecordMergeRanges(int recordsCount,
                                                            int rangeSize,
                                                            InventoryRecordType recordType,
                                                            String tenantId) {
    log.info("Constructing Merge Ranges: [recordType: {}, recordsCount: {}, tenant: {}]",
      recordType, recordsCount, tenantId);

    List<MergeRangeEntity> ranges = new ArrayList<>();
    int pages = (int) Math.ceil((double) recordsCount / rangeSize);
    var recordIds = inventoryService.fetchInventoryRecordIds(recordType, null, 0, rangeSize);
    if (CollectionUtils.isEmpty(recordIds)) {
      log.warn("There are no records to create merge ranges: [recordType: {}, recordsCount: {}, tenant: {}]",
        recordType, recordsCount, tenantId);
      return Collections.emptyList();
    }
    var lowerId = recordIds.get(0);
    var upperId = recordIds.get(recordIds.size() - 1);
    ranges.add(
      new MergeRangeEntity(UUID.randomUUID(), recordType, tenantId, lowerId, upperId, Timestamp.from(Instant.now())));
    for (int i = 1; i < pages; i++) {
      int offset = i * rangeSize;
      int limit = Math.min(rangeSize, recordsCount - offset);
      var query = CqlQuery.greaterThan(CqlQueryParam.ID, lowerId.toString());
      recordIds = inventoryService.fetchInventoryRecordIds(recordType, query, offset, limit);
      lowerId = recordIds.get(0);
      upperId = recordIds.get(recordIds.size() - 1);
      ranges.add(
        new MergeRangeEntity(UUID.randomUUID(), recordType, tenantId, lowerId, upperId, Timestamp.from(Instant.now())));
    }

    return ranges;
  }
}
