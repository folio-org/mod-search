package org.folio.search.integration;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.client.InventoryInstanceClient;
import org.folio.search.client.InventoryReindexRecordsClient;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Log4j2
public class InventoryService {

  private final InventoryInstanceClient inventoryInstanceClient;
  private final InventoryReindexRecordsClient reindexRecordsClient;

  public InventoryService(InventoryInstanceClient inventoryInstanceClient,
                          InventoryReindexRecordsClient reindexRecordsClient) {
    this.inventoryInstanceClient = inventoryInstanceClient;
    this.reindexRecordsClient = reindexRecordsClient;
  }

  public int fetchInventoryRecordsCount(InventoryRecordType recordType) {
    if (recordType == null) {
      log.warn("No Inventory Record Type was provided to fetch Inventory Count");
      return 0;
    }

    try {
      var countPath = "%s?limit=0&totalRecords=exact".formatted(recordType.getPath());
      var uri = UriComponentsBuilder.fromUriString(countPath).build().toUri();
      var result = inventoryInstanceClient.getInventoryRecordsCount(uri);

      if (result == null) {
        log.warn("Failed to retrieve Inventory Records count");
        return 0;
      }

      return result.totalRecords();
    } catch (Exception e) {
      log.error("Failed to fetch Inventory record counts for {}", recordType);
      throw new FolioIntegrationException(
        "Failed to fetch inventory record counts for %s : %s".formatted(recordType.name(), e.getMessage()), e);
    }
  }

  public void publishReindexRecordsRange(MergeRangeEntity rangeEntity) {
    if (rangeEntity == null
      || ObjectUtils.anyNull(rangeEntity.getId(), rangeEntity.getLowerId(), rangeEntity.getUpperId())) {
      log.warn("Invalid Range Entity: [rangeEntity: {}]", rangeEntity);
      return;
    }

    var from = rangeEntity.getLowerId().toString();
    var to = rangeEntity.getUpperId().toString();
    var recordsRange = new InventoryReindexRecordsClient.ReindexRecords(
      rangeEntity.getId().toString(),
      rangeEntity.getEntityType().getType(),
      new InventoryReindexRecordsClient.ReindexRecordsRange(from, to));

    try {
      reindexRecordsClient.publishReindexRecords(recordsRange);
    } catch (Exception e) {
      log.warn("Failed to publish reindex records range {} : {}", recordsRange, e.getMessage());
      throw new FolioIntegrationException("Failed to publish reindex records range", e);
    }
  }
}
