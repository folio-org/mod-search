package org.folio.search.service.indexrebuild.records;

import static org.folio.search.configuration.RetryTemplateConfiguration.ITEM_INDEXING_RETRY_TEMPLATE_NAME;
import static org.folio.search.model.types.SearchOperationType.BULK;
import static org.folio.search.model.types.SearchOperationType.REFRESH;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.exception.IndexingFailureException;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.IndexingFailureUtils;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.rest.RestStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes records to the index of a target. Documents are only created: the index was emptied when the run started, so
 * an existing document was written by an event and is newer than the record, and redelivered records change nothing.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class RecordsIndexWriter {

  private final MultiTenantSearchDocumentConverter documentConverter;
  private final IndexNameProvider indexNameProvider;
  private final RestHighLevelClient elasticsearchClient;
  @Qualifier(ITEM_INDEXING_RETRY_TEMPLATE_NAME)
  private final RetryTemplate retryTemplate;

  /**
   * Creates documents for the given records of a tenant.
   *
   * @throws IndexingFailureException if documents were rejected
   */
  public void write(RecordsRebuildStrategy strategy, String tenantId, List<Map<String, Object>> records) {
    if (records.isEmpty()) {
      return;
    }
    var events = records.stream().map(source -> toEvent(strategy, tenantId, source)).toList();
    var documents = documentConverter.convert(events).values().stream().flatMap(Collection::stream).toList();
    retryTemplate.invoke(() -> {
      var request = new BulkRequest();
      documents.forEach(document -> request.add(toCreateRequest(document)));
      throwOnFailures(execute(request, () -> "create documents"));
    });
    log.debug("write:: created documents [resource: {}, tenant: {}, count: {}]",
      strategy.target().getResource(), tenantId, documents.size());
  }

  /**
   * Removes documents of the given records of a tenant.
   *
   * @throws IndexingFailureException if documents couldn't be removed
   */
  public void delete(RecordsRebuildStrategy strategy, String tenantId, List<String> recordIds) {
    if (recordIds.isEmpty()) {
      return;
    }
    var index = indexNameProvider.getIndexName(strategy.target().getResourceType(), tenantId);
    retryTemplate.invoke(() -> {
      var request = new BulkRequest();
      recordIds.forEach(id -> request.add(new DeleteRequest(index).id(strategy.documentId(id, tenantId))));
      throwOnFailures(execute(request, () -> "delete documents"));
    });
    log.debug("delete:: removed documents [resource: {}, tenant: {}, count: {}]", strategy.target().getResource(),
      tenantId, recordIds.size());
  }

  /**
   * Makes written documents searchable.
   */
  public void refresh(RecordsRebuildStrategy strategy, String tenantId) {
    var index = indexNameProvider.getIndexName(strategy.target().getResourceType(), tenantId);
    var refreshRequest = new RefreshRequest(index);
    performExceptionalOperation(() -> elasticsearchClient.indices().refresh(refreshRequest, DEFAULT), index, REFRESH);
  }

  private static ResourceEvent toEvent(RecordsRebuildStrategy strategy, String tenantId, Map<String, Object> source) {
    var prepared = strategy.prepare(tenantId, source);
    return new ResourceEvent()
      .id(strategy.documentId(String.valueOf(prepared.get(ID_FIELD)), tenantId))
      .type(ResourceEventType.REINDEX)
      .tenant(tenantId)
      .resourceName(strategy.target().getResourceType().getName())
      ._new(prepared);
  }

  private IndexRequest toCreateRequest(SearchDocumentBody document) {
    return new IndexRequest(indexNameProvider.getIndexName(document))
      .id(document.getId())
      .source(document.getDocumentBody(), document.getDataFormat().getXcontentType())
      .opType(DocWriteRequest.OpType.CREATE);
  }

  private BulkResponse execute(BulkRequest request, Supplier<String> description) {
    return performExceptionalOperation(() -> elasticsearchClient.bulk(request, DEFAULT), description.get(), BULK);
  }

  private static void throwOnFailures(BulkResponse response) {
    var failures = Arrays.stream(response.getItems())
      .filter(BulkItemResponse::isFailed)
      .map(BulkItemResponse::getFailure)
      .filter(failure -> failure.getStatus() != RestStatus.CONFLICT)
      .toList();
    if (failures.isEmpty()) {
      return;
    }
    var message = failures.stream()
      .map(failure -> "index [%s], id [%s], status [%s], message [%s]".formatted(failure.getIndex(), failure.getId(),
        failure.getStatus().getStatus(), IndexingFailureUtils.getCauseChainMessage(failure.getCause())))
      .collect(Collectors.joining("; ", "Failed to write documents: ", ""));
    var status = failures.stream()
      .map(failure -> failure.getStatus().getStatus())
      .filter(IndexingFailureException::isRetryableStatus)
      .findFirst()
      .orElse(failures.getFirst().getStatus().getStatus());
    throw new IndexingFailureException(message, status,
      IndexingFailureUtils.parseErrorType(failures.getFirst().getMessage()));
  }
}
