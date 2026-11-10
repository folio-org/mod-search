package org.folio.search.service;

import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;
import static org.folio.search.configuration.RetryTemplateConfiguration.ITEM_INDEXING_RETRY_TEMPLATE_NAME;
import static org.folio.search.utils.LogUtils.collectionToLogMsg;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.consortium.SharedInstanceResolver;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.IndexingFailureUtils;
import org.folio.spring.exception.FolioContextExecutionException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.script.Script;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Indexes inventory item events as standalone documents into the item index.
 *
 * <p>Enabled only when {@code folio.search-config.indexing.item-index-enabled} is {@code true}. Items are not
 * persisted to the merge-range DB here and parent instances are not re-indexed — that is handled by the instance
 * indexing flow. Batches are retried with {@code itemIndexingRetryTemplate} (client errors are not retried) and then
 * processed one by one; events that still fail are stored via {@link FailedIndexingEventService}.</p>
 *
 * <p>In a consortium item documents carry the {@code shared} flag of their instance, which the search uses to return
 * items of shared instances from all tenants and items of local instances only to their tenant. The flag is resolved
 * from the central instance table on indexing and set on existing documents when an instance is created in the central
 * tenant (a local instance became shared, or the instance event arrived after its items).</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "folio.search-config.indexing.item-index-enabled", havingValue = "true")
public class ItemIndexService {

  private static final Script MARK_SHARED_SCRIPT = new Script("ctx._source.shared = true");

  private final PrimaryResourceRepository primaryResourceRepository;
  private final MultiTenantSearchDocumentConverter searchDocumentConverter;
  private final FolioMessageBatchProcessor folioMessageBatchProcessor;
  private final EgressExecutionContextService executionService;
  private final FailedIndexingEventService failedIndexingEventService;
  private final SharedInstanceResolver sharedInstanceResolver;
  private final ConsortiumTenantProvider consortiumTenantProvider;

  /**
   * Indexes item events from the given inventory events as standalone documents into the item index. Non-item events
   * are ignored and the given events are not modified. Events are processed per tenant with retry and one-by-one
   * fallback; events that still fail are logged and stored as failed indexing events.
   *
   * @param events inventory events as {@link ResourceEvent} objects
   * @return success response, or error response with the number of item events that failed and were stored
   */
  public FolioIndexOperationResponse indexItemEvents(List<ResourceEvent> events) {
    var candidateEvents = events.stream()
      .filter(event -> isItemEvent(event) || isInstanceCreate(event))
      .map(ItemIndexService::copyOf)
      .toList();
    if (candidateEvents.isEmpty()) {
      return getSuccessIndexOperationResponse();
    }

    log.debug("indexItemEvents:: by [events.size: {}]", collectionToLogMsg(candidateEvents, true));
    var failedEvents = new ArrayList<ResourceEvent>();
    var processedEvents = new ArrayList<ResourceEvent>();
    candidateEvents.stream()
      .collect(Collectors.groupingBy(ResourceEvent::getTenant))
      .forEach((tenant, tenantEvents) -> executionService.execute(tenant, () -> {
        var batch = selectTenantBatch(tenant, tenantEvents);
        processedEvents.addAll(batch);
        processBatch(batch, failedEvents);
        return null;
      }));

    return toResponse(failedEvents, processedEvents.size());
  }

  /**
   * Indexes item records pulled from inventory by an items-only reindex as standalone documents into the item index.
   * Documents are built exactly like the ones of item events (tenant scoped id, {@code tenantId}, {@code shared} flag,
   * flattened fields), so a reindexed item is indistinguishable from an event-indexed one.
   *
   * <p>Documents are only created: an item index is recreated before the run, so an existing document was written by
   * an event that is newer than the reindex record and must not be overwritten. Such conflicts are not failures. The
   * batch is retried with {@code itemIndexingRetryTemplate} and the failure is rethrown, so the caller can mark the
   * merge range as failed. There is no one-by-one fallback and nothing is stored as failed indexing event.</p>
   *
   * @param tenant  tenant the records belong to
   * @param records item records as received from inventory
   * @throws IndexingFailureException if OpenSearch rejected documents
   */
  public void reindexItems(String tenant, List<Map<String, Object>> records) {
    var events = records.stream()
      .filter(record -> isNotEmpty(record.get("id")))
      .map(record -> new ResourceEvent()
        .id(String.valueOf(record.get("id")))
        .type(ResourceEventType.REINDEX)
        .tenant(tenant)
        .resourceName(ResourceType.ITEM.getName())
        ._new(new LinkedHashMap<>(record)))
      .toList();
    if (events.size() != records.size()) {
      log.warn("reindexItems:: skipped records without id [tenant: {}, skipped: {}]",
        tenant, records.size() - events.size());
    }
    if (events.isEmpty()) {
      return;
    }

    try {
      executionService.execute(tenant, () -> createDocumentsWithRetry(events));
    } catch (FolioContextExecutionException e) {
      // keep the failure itself, its message is stored as the failure cause of the merge range
      throw e.getCause() instanceof RuntimeException cause ? cause : e;
    }
  }

  private Void createDocumentsWithRetry(List<ResourceEvent> events) {
    folioMessageBatchProcessor.executeWithRetry(ITEM_INDEXING_RETRY_TEMPLATE_NAME, () -> createDocuments(events));
    return null;
  }

  private void createDocuments(List<ResourceEvent> events) {
    populateSharedFlag(events);
    var documents = convertToDocuments(events.stream().map(ItemIndexService::withTenantScopedId).toList());
    var bulkResponse = primaryResourceRepository.executeBulkCreate(documents);
    var failures = failedItems(bulkResponse, true);
    if (!failures.isEmpty()) {
      var failure = toIndexingFailure(failures);
      log.warn("reindexItems:: failed to index documents [indexRequests: {}, status: {}, errorType: {}]",
        documents.size(), failure.getStatus(), failure.getErrorType());
      throw failure;
    }
    log.info("reindexItems:: indexed to elasticsearch [indexRequests: {}, existing: {}]", documents.size(),
      Arrays.stream(bulkResponse.getItems()).filter(BulkItemResponse::isFailed).count());
  }

  private void processBatch(List<ResourceEvent> batch, List<ResourceEvent> failedEvents) {
    if (batch.isEmpty()) {
      return;
    }
    folioMessageBatchProcessor.consumeBatchWithFallback(batch, ITEM_INDEXING_RETRY_TEMPLATE_NAME,
      this::indexBatch, (event, exception) -> {
        handleFailedEvent(event, exception);
        failedEvents.add(event);
      });
  }

  /**
   * Instance create events are relevant only for the central tenant, where they make the instance shared.
   */
  private List<ResourceEvent> selectTenantBatch(String tenant, List<ResourceEvent> tenantEvents) {
    var hasInstanceEvents = tenantEvents.stream().anyMatch(ItemIndexService::isInstanceCreate);
    if (hasInstanceEvents && consortiumTenantProvider.isCentralTenant(tenant)) {
      return tenantEvents;
    }
    return tenantEvents.stream().filter(ItemIndexService::isItemEvent).toList();
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
    var eventsByItem = itemEvents.stream().collect(Collectors.partitioningBy(ItemIndexService::isItemEvent));
    var eventsByDeleteAll = eventsByItem.get(true).stream()
      .collect(Collectors.partitioningBy(ItemIndexService::isDeleteAll));
    deleteAllForTenants(eventsByDeleteAll.get(true));
    indexDocuments(eventsByDeleteAll.get(false));
    markItemsOfInstancesShared(eventsByItem.get(false));
  }

  private void markItemsOfInstancesShared(List<ResourceEvent> instanceEvents) {
    if (instanceEvents.isEmpty()) {
      return;
    }

    var instanceIds = instanceEvents.stream().map(ResourceEvent::getId).toList();
    var tenant = instanceEvents.getFirst().getTenant();
    var query = boolQuery()
      .filter(termsQuery(INSTANCE_ID_FIELD, instanceIds))
      .mustNot(termQuery(SHARED_FIELD_NAME, true));
    var response = primaryResourceRepository.updateByQuery(query, ResourceType.ITEM, MARK_SHARED_SCRIPT, tenant);
    if (response.getStatus() == FolioIndexOperationResponse.StatusEnum.ERROR) {
      throw new IndexingFailureException(
        "Failed to mark items of shared instances [ids: %s, errors: [%s]]"
          .formatted(instanceIds, response.getErrorMessage()), null, null);
    }
  }

  private void populateSharedFlag(List<ResourceEvent> events) {
    var instanceIds = events.stream()
      .map(ItemIndexService::getInstanceId)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    if (instanceIds.isEmpty()) {
      return;
    }

    var sharedIds = sharedInstanceResolver.findSharedInstanceIds(events.getFirst().getTenant(), instanceIds);
    events.forEach(event -> {
      if (sharedIds.contains(getInstanceId(event))) {
        newPartAsMap(event).put(SHARED_FIELD_NAME, true);
      }
    });
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

    populateSharedFlag(events);
    var documents = convertToDocuments(events.stream().map(ItemIndexService::withTenantScopedId).toList());
    var bulkResponse = primaryResourceRepository.executeBulk(documents);
    if (bulkResponse.hasFailures()) {
      var failure = toIndexingFailure(failedItems(bulkResponse, false));
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
  private static List<BulkItemResponse.Failure> failedItems(BulkResponse bulkResponse, boolean ignoreConflicts) {
    return Arrays.stream(bulkResponse.getItems())
      .filter(BulkItemResponse::isFailed)
      .map(BulkItemResponse::getFailure)
      .filter(failure -> !ignoreConflicts || failure.getStatus() != RestStatus.CONFLICT)
      .toList();
  }

  private static IndexingFailureException toIndexingFailure(List<BulkItemResponse.Failure> failures) {
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

  private static boolean isItemEvent(ResourceEvent event) {
    return ResourceType.ITEM.getName().equals(event.getResourceName());
  }

  private static boolean isInstanceCreate(ResourceEvent event) {
    return ResourceType.INSTANCE.getName().equals(event.getResourceName())
           && event.getType() == ResourceEventType.CREATE;
  }

  private static String getInstanceId(ResourceEvent event) {
    return event.getNew() instanceof Map<?, ?> map && map.get(INSTANCE_ID_FIELD) instanceof String id ? id : null;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> newPartAsMap(ResourceEvent event) {
    return (Map<String, Object>) event.getNew();
  }

  private static boolean isDeleteAll(ResourceEvent event) {
    return event.getType() == ResourceEventType.DELETE_ALL;
  }
}
