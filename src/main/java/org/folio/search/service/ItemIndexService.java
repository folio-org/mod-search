package org.folio.search.service;

import static org.folio.search.configuration.RetryTemplateConfiguration.ITEM_INDEXING_RETRY_TEMPLATE_NAME;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.exception.IndexingFailureException;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.IndexingFailureUtils;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Indexes inventory item events as standalone documents into the item index.
 *
 * <p>Enabled only when {@code folio.search-config.indexing.item-index-enabled} is {@code true}. Items are not
 * persisted to the merge-range DB here and parent instances are not re-indexed — that is handled by the instance
 * indexing flow. Batches are retried with {@code itemIndexingRetryTemplate} (client errors are not retried) and then
 * processed one by one; events that still fail are stored via {@link FailedIndexingEventService}.</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "folio.search-config.indexing.item-index-enabled", havingValue = "true")
public class ItemIndexService {

  private final PrimaryResourceRepository primaryResourceRepository;
  private final MultiTenantSearchDocumentConverter searchDocumentConverter;
  private final FolioMessageBatchProcessor folioMessageBatchProcessor;
  private final EgressExecutionContextService executionService;
  private final FailedIndexingEventService failedIndexingEventService;

  /**
   * Indexes item events from the given inventory events as standalone documents into the item index. Non-item events
   * are ignored and the given events are not modified. Events are processed per tenant with retry and one-by-one
   * fallback; events that still fail are logged and stored as failed indexing events.
   *
   * @param events inventory events as {@link ResourceEvent} objects
   * @return success response, or error response with the number of item events that failed and were stored
   */
  public FolioIndexOperationResponse indexItemEvents(List<ResourceEvent> events) {
    var itemEvents = events.stream()
      .filter(event -> ResourceType.ITEM.getName().equals(event.getResourceName()))
      .map(ItemIndexService::copyOf)
      .toList();
    if (itemEvents.isEmpty()) {
      return getSuccessIndexOperationResponse();
    }

    log.debug("indexItemEvents:: by [itemEvents.size: {}]", collectionToLogMsg(itemEvents, true));
    var failedEvents = new ArrayList<ResourceEvent>();
    itemEvents.stream()
      .collect(Collectors.groupingBy(ResourceEvent::getTenant))
      .forEach((tenant, tenantEvents) -> executionService.execute(tenant, () -> {
        folioMessageBatchProcessor.consumeBatchWithFallback(tenantEvents, ITEM_INDEXING_RETRY_TEMPLATE_NAME,
          this::indexBatch, (event, exception) -> {
            handleFailedEvent(event, exception);
            failedEvents.add(event);
          });
        return null;
      }));

    return toResponse(failedEvents, itemEvents.size());
  }

  private static FolioIndexOperationResponse toResponse(List<ResourceEvent> failedEvents, int totalEvents) {
    if (failedEvents.isEmpty()) {
      return getSuccessIndexOperationResponse();
    }
    var failedIds = failedEvents.stream().map(ResourceEvent::getId).collect(Collectors.joining(", "));
    return getErrorIndexOperationResponse(
      "Failed to index %d of %d item events, failures are stored in failed_indexing_event [ids: %s]"
        .formatted(failedEvents.size(), totalEvents, failedIds));
  }

  /**
   * Indexes a batch of item events of a single tenant. Document id is {@code <itemId>|<tenant>} so the same item from
   * different tenants of a consortium can coexist in the central index. {@code DELETE_ALL} events remove all item
   * documents of the tenant.
   *
   * @throws IndexingFailureException if OpenSearch rejected documents or the delete-all request failed
   */
  private void indexBatch(List<ResourceEvent> itemEvents) {
    var eventsByDeleteAll = itemEvents.stream().collect(Collectors.partitioningBy(ItemIndexService::isDeleteAll));
    deleteAllForTenants(eventsByDeleteAll.get(true));
    indexDocuments(eventsByDeleteAll.get(false));
  }

  private void handleFailedEvent(ResourceEvent event, Exception exception) {
    log.warn(new FormattedMessage(
      "handleFailedEvent:: Failed to index item event [eventType: {}, tenantId: {}, id: {}]",
      event.getType(), event.getTenant(), event.getId()), exception);
    failedIndexingEventService.save(event, exception);
  }

  private void deleteAllForTenants(List<ResourceEvent> deleteAllEvents) {
    deleteAllEvents.stream()
      .map(ResourceEvent::getTenant)
      .distinct()
      .forEach(this::deleteAllForTenant);
  }

  private void deleteAllForTenant(String tenant) {
    var response = primaryResourceRepository.deleteResourceByTenantId(ResourceType.ITEM, tenant);
    if (response.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      throw new IndexingFailureException(
        "Failed to delete item documents [tenant: %s, errors: [%s]]".formatted(tenant, response.getErrorMessage()),
        null, null);
    }
  }

  private void indexDocuments(List<ResourceEvent> events) {
    if (events.isEmpty()) {
      return;
    }

    var documents = convertToDocuments(events.stream().map(ItemIndexService::withTenantScopedId).toList());
    var bulkResponse = primaryResourceRepository.executeBulk(documents);
    if (bulkResponse.hasFailures()) {
      var failure = toIndexingFailure(bulkResponse);
      log.warn("indexItemEvents:: failed to index documents [indexRequests: {}, status: {}, errorType: {}]",
        documents.size(), failure.getStatus(), failure.getErrorType());
      throw failure;
    }
    log.info("indexItemEvents:: indexed to elasticsearch [indexRequests: {}]", documents.size());
  }

  /**
   * Builds failure from failed bulk items. Status and error type are taken from the first failed item; the failure is
   * marked with a retryable status if any failed item is retryable, so a mixed batch is still retried.
   */
  private static IndexingFailureException toIndexingFailure(BulkResponse bulkResponse) {
    var failures = Arrays.stream(bulkResponse.getItems())
      .filter(BulkItemResponse::isFailed)
      .map(BulkItemResponse::getFailure)
      .toList();
    var message = failures.stream()
      .map(failure -> "index [%s], id [%s], status [%s], message [%s]".formatted(failure.getIndex(), failure.getId(),
        failure.getStatus().getStatus(), IndexingFailureUtils.getCauseChainMessage(failure.getCause())))
      .collect(Collectors.joining("; ", "Failed to index item documents: ", ""));
    var first = failures.getFirst();
    var status = failures.stream()
      .map(failure -> failure.getStatus().getStatus())
      .filter(IndexingFailureException::isRetryableStatus)
      .findFirst()
      .orElse(first.getStatus().getStatus());
    var errorType = IndexingFailureUtils.parseErrorType(first.getMessage());
    return new IndexingFailureException(message, status, errorType);
  }

  private List<SearchDocumentBody> convertToDocuments(List<ResourceEvent> events) {
    return searchDocumentConverter.convert(events).values().stream()
      .flatMap(Collection::stream)
      .toList();
  }

  private static ResourceEvent withTenantScopedId(ResourceEvent event) {
    return new ResourceEvent()
      .id(event.getId() + "|" + event.getTenant())
      .type(event.getType())
      .deleteEventSubType(event.getDeleteEventSubType())
      .tenant(event.getTenant())
      .resourceName(event.getResourceName())
      ._new(event.getNew())
      .old(event.getOld());
  }

  private static ResourceEvent copyOf(ResourceEvent event) {
    var newPart = event.getNew() instanceof Map<?, ?> map ? new LinkedHashMap<>(map) : event.getNew();
    return new ResourceEvent()
      .id(event.getId())
      .type(event.getType())
      .deleteEventSubType(event.getDeleteEventSubType())
      .tenant(event.getTenant())
      .resourceName(event.getResourceName())
      ._new(newPart)
      .old(event.getOld());
  }

  private static boolean isDeleteAll(ResourceEvent event) {
    return event.getType() == ResourceEventType.DELETE_ALL;
  }
}
