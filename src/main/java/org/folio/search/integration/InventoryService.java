package org.folio.search.integration;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.client.InventoryHoldingClient;
import org.folio.search.client.InventoryInstanceClient;
import org.folio.search.client.InventoryItemClient;
import org.folio.search.client.InventoryReindexRecordsClient;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.model.client.CqlQuery;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class InventoryService {

  private final InventoryInstanceClient inventoryInstanceClient;
  private final InventoryItemClient inventoryItemClient;
  private final InventoryHoldingClient inventoryHoldingClient;
  private final InventoryReindexRecordsClient reindexRecordsClient;

  public InventoryService(InventoryInstanceClient inventoryInstanceClient,
                          InventoryItemClient inventoryItemClient,
                          InventoryHoldingClient inventoryHoldingClient,
                          InventoryReindexRecordsClient reindexRecordsClient) {
    this.inventoryInstanceClient = inventoryInstanceClient;
    this.inventoryItemClient = inventoryItemClient;
    this.inventoryHoldingClient = inventoryHoldingClient;
    this.reindexRecordsClient = reindexRecordsClient;
  }

  public List<UUID> fetchInventoryRecordIds(InventoryRecordType recordType, CqlQuery cqlQuery, int offset, int limit) {
    if (recordType == null) {
      log.warn("No Inventory Record Type was provided to fetch Inventory Record");
      return Collections.emptyList();
    }

    try {
      return switch (recordType) {
        case INSTANCE -> fetchInstances(cqlQuery, offset, limit);
        case ITEM -> fetchItems(cqlQuery, offset, limit);
        case HOLDING -> fetchHoldings(cqlQuery, offset, limit);
      };
    } catch (Exception e) {
      log.warn("Failed to fetch Inventory records for {} : {}", recordType, e.getMessage());
      throw new FolioIntegrationException("Failed to fetch inventory records for %s".formatted(recordType.name()), e);
    }
  }

  public int fetchInventoryRecordCount(InventoryRecordType recordType) {
    if (recordType == null) {
      log.warn("No Inventory Record Type was provided to fetch Inventory Count");
      return 0;
    }

    try {
      var result = switch (recordType) {
        case INSTANCE -> inventoryInstanceClient.getInstancesCount();
        case ITEM -> inventoryItemClient.getItemsCount();
        case HOLDING -> inventoryHoldingClient.getHoldingsCount();
      };

      if (result == null) {
        log.warn("Failed to retrieve Inventory Instances count");
        return 0;
      }

      return result.totalRecords();
    } catch (Exception e) {
      log.warn("Failed to fetch Inventory record counts for {}", recordType);
      throw new FolioIntegrationException(
        "Failed to fetch inventory record counts for %s : %s".formatted(recordType.name(), e.getMessage()));
    }
  }

  public void publishReindexRecordsRange(MergeRangeEntity rangeEntity) {
    if (rangeEntity == null
      || ObjectUtils.anyNull(rangeEntity.getId(), rangeEntity.getLowerId(), rangeEntity.getUpperId())) {
      log.warn("invalid Range Entity: [rangeEntity: {}]", rangeEntity);
      return;
    }

    var from = rangeEntity.getLowerId().toString();
    var to = rangeEntity.getUpperId().toString();
    var recordsRange = new InventoryReindexRecordsClient.ReindexRecords(
      rangeEntity.getId().toString(),
      rangeEntity.getEntityType().name(),
      new InventoryReindexRecordsClient.ReindexRecordsRange(from, to));

    try {
      reindexRecordsClient.publishReindexRecords(recordsRange);
    } catch (Exception e) {
      log.warn("Failed to publish reindex records range {} : {}", recordsRange, e.getMessage());
      throw new FolioIntegrationException("Failed to publish reindex records range", e);
    }
  }

  private List<UUID> fetchInstances(CqlQuery cqlQuery, int offset, int limit) {
    var result = Optional.ofNullable(cqlQuery)
      .map(query -> inventoryInstanceClient.getInstances(query, offset, limit))
      .orElseGet(() -> inventoryInstanceClient.getInstances(offset, limit));

    if (result == null) {
      log.warn("Failed to retrieve Inventory Instances, [query: {}, offset: {}, limit: {}]", cqlQuery, offset, limit);
      return Collections.emptyList();
    }

    return result.instances().stream()
      .map(InventoryInstanceClient.InventoryInstanceDto::id)
      .map(UUID::fromString)
      .toList();
  }

  private List<UUID> fetchItems(CqlQuery cqlQuery, int offset, int limit) {
    var result = Optional.ofNullable(cqlQuery)
      .map(query -> inventoryItemClient.getItems(query, offset, limit))
      .orElseGet(() -> inventoryItemClient.getItems(offset, limit));

    if (result == null) {
      log.warn("Failed to retrieve Inventory Items, [query: {}, offset: {}, limit: {}]", cqlQuery, offset, limit);
      return Collections.emptyList();
    }

    return result.items().stream()
      .map(InventoryItemClient.InventoryItemDto::id)
      .map(UUID::fromString)
      .toList();
  }

  private List<UUID> fetchHoldings(CqlQuery cqlQuery, int offset, int limit) {
    var result = Optional.ofNullable(cqlQuery)
      .map(query -> inventoryHoldingClient.getHoldings(query, offset, limit))
      .orElseGet(() -> inventoryHoldingClient.getHoldings(offset, limit));

    if (result == null) {
      log.warn("Failed to retrieve Inventory Holdings, [query: {}, offset: {}, limit: {}]", cqlQuery, offset, limit);
      return Collections.emptyList();
    }

    return result.holdingsRecords().stream()
      .map(InventoryHoldingClient.InventoryHoldingDto::id)
      .map(UUID::fromString)
      .toList();
  }
}
