package org.folio.search.integration.folio;

import static org.folio.search.configuration.RetryTemplateConfiguration.REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.client.InventoryInstanceClient;
import org.folio.search.client.InventoryReindexRecordsClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.search.model.reindex.MergeRangeEntity;
import org.folio.search.model.types.InventoryRecordType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Log4j2
public class InventoryService {

  private final InventoryInstanceClient inventoryInstanceClient;
  private final InventoryReindexRecordsClient reindexRecordsClient;
  private final ReindexConfigurationProperties reindexConfig;
  private final RetryTemplate retryTemplate;

  public InventoryService(InventoryInstanceClient inventoryInstanceClient,
                          InventoryReindexRecordsClient reindexRecordsClient,
                          ReindexConfigurationProperties reindexConfig,
                          @Qualifier(value = REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME) RetryTemplate retryTemplate) {
    this.inventoryInstanceClient = inventoryInstanceClient;
    this.reindexRecordsClient = reindexRecordsClient;
    this.reindexConfig = reindexConfig;
    this.retryTemplate = retryTemplate;
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
      log.error(new FormattedMessage("Failed to fetch Inventory record counts for {}", recordType), e);
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
    retryTemplate.invoke(() -> {
      if (reindexConfig.getReindexType() == ReindexConfigurationProperties.ReindexType.PUBLISH) {
        var recordsRange = InventoryReindexRecordsClient.constructRequest(
          rangeEntity.getId().toString(),
          rangeEntity.getEntityType().getType(),
          rangeEntity.getLowerId(),
          rangeEntity.getUpperId());
        reindexRecordsClient.publishReindexRecords(recordsRange);
      } else {
        var recordsRange = InventoryReindexRecordsClient.constructRequest(
          rangeEntity.getId().toString(),
          rangeEntity.getTraceId() == null ? null : rangeEntity.getTraceId().toString(),
          rangeEntity.getEntityType().getType(),
          rangeEntity.getLowerId(),
          rangeEntity.getUpperId());
        reindexRecordsClient.exportReindexRecords(recordsRange);
      }
    });
  }
}
