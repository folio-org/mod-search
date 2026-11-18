package org.folio.search.service.indexrebuild.records;

import static org.folio.search.configuration.RetryTemplateConfiguration.REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.folio.search.client.InventoryReindexRecordsClient;
import org.folio.search.configuration.properties.ReindexConfigurationProperties;
import org.folio.search.integration.folio.InventoryService;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.indexrebuild.IndexRebuildRangeIds;
import org.folio.search.service.indexrebuild.model.IndexRebuildRange;
import org.folio.search.service.indexrebuild.model.IndexRebuildRangeStatus;
import org.folio.search.service.reindex.RangeGenerator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Splits records of a tenant into ranges and asks inventory to deliver them.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RecordsRangePublisher {

  private static final int PUBLISH_PARTITION_SIZE = 2000;

  private final InventoryService inventoryService;
  private final InventoryReindexRecordsClient reindexRecordsClient;
  private final ReindexConfigurationProperties reindexConfig;
  private final EgressExecutionContextService executionService;
  @Qualifier("reindexPublisherExecutor")
  private final ExecutorService publisherExecutor;
  @Qualifier(REINDEX_PUBLISH_RANGE_RETRY_TEMPLATE_NAME)
  private final RetryTemplate retryTemplate;

  public List<IndexRebuildRange> createRanges(RecordsRebuildStrategy strategy, String tenantId) {
    var count = executionService.execute(tenantId,
      () -> inventoryService.fetchInventoryRecordsCount(strategy.inventoryRecordType()));
    var traceId = UUID.randomUUID();
    if (count == 0) {
      log.info("createRanges:: no records, creating one empty range [resource: {}, tenant: {}]",
        strategy.target().getResource(), tenantId);
      return List.of(toRange(strategy, tenantId, traceId, RangeGenerator.emptyUuidRange()));
    }
    var rangesCount = (int) Math.ceil((double) count / reindexConfig.getMergeRangeSize());
    log.info("createRanges:: creating ranges [resource: {}, tenant: {}, records: {}, ranges: {}, traceId: {}]",
      strategy.target().getResource(), tenantId, count, rangesCount, traceId);
    return RangeGenerator.createUuidRanges(rangesCount).stream()
      .map(range -> toRange(strategy, tenantId, traceId, range))
      .toList();
  }

  /**
   * Requests delivery of the given ranges and waits until all requests are accepted.
   */
  public void publish(RecordsRebuildStrategy strategy, List<IndexRebuildRange> ranges) {
    var futures = new ArrayList<CompletableFuture<Void>>();
    for (var partition : ListUtils.partition(ranges, PUBLISH_PARTITION_SIZE)) {
      futures.add(CompletableFuture.runAsync(() -> partition.forEach(range -> publish(strategy, range)),
        publisherExecutor));
    }
    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
  }

  private void publish(RecordsRebuildStrategy strategy, IndexRebuildRange range) {
    executionService.execute(range.tenantId(), () -> {
      retryTemplate.invoke(() -> sendRequest(strategy, range));
      return null;
    });
  }

  private void sendRequest(RecordsRebuildStrategy strategy, IndexRebuildRange range) {
    var id = range.id().toString();
    log.debug("sendRequest:: requesting records [resource: {}, tenant: {}, rangeId: {}, type: {}]",
      strategy.target().getResource(), range.tenantId(), id, reindexConfig.getReindexType());
    var recordType = strategy.inventoryRecordType().getRecordName();
    if (reindexConfig.getReindexType() == ReindexConfigurationProperties.ReindexType.PUBLISH) {
      reindexRecordsClient.publishReindexRecords(
        InventoryReindexRecordsClient.constructRequest(id, recordType, range.lower(), range.upper()));
    } else {
      reindexRecordsClient.exportReindexRecords(InventoryReindexRecordsClient.constructRequest(id,
        range.traceId().toString(), recordType, range.lower(), range.upper()));
    }
  }

  private static IndexRebuildRange toRange(RecordsRebuildStrategy strategy, String tenantId, UUID traceId,
                                           RangeGenerator.Range range) {
    return new IndexRebuildRange(IndexRebuildRangeIds.newId(), traceId, strategy.target(), tenantId,
      range.lowerBound(), range.upperBound(), IndexRebuildRangeStatus.PENDING, null);
  }
}
