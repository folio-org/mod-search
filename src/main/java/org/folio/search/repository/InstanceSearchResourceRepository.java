package org.folio.search.repository;

import static java.util.stream.Collectors.joining;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.InstanceSearchDocumentBody;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.VersionType;
import org.springframework.stereotype.Repository;

/**
 * Repository for indexing flat instance search documents.
 * Uses explicit target index, routing key, and external versioning from InstanceSearchDocumentBody.
 */
@Log4j2
@Repository
public class InstanceSearchResourceRepository {

  private final RestHighLevelClient elasticsearchClient;

  public InstanceSearchResourceRepository(RestHighLevelClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
  }

  public FolioIndexOperationResponse indexResources(List<InstanceSearchDocumentBody> documents) {
    if (CollectionUtils.isEmpty(documents)) {
      return getSuccessIndexOperationResponse();
    }

    var bulkRequest = new BulkRequest();
    for (var document : documents) {
      if (document.getAction() == org.folio.search.model.types.IndexActionType.INDEX) {
        bulkRequest.add(prepareIndexRequest(document));
      } else {
        bulkRequest.add(prepareDeleteRequest(document));
      }
    }

    var indicesString = bulkRequest.requests().stream().map(DocWriteRequest::index).collect(joining(","));
    var bulkResponse = performExceptionalOperation(
      () -> elasticsearchClient.bulk(bulkRequest, DEFAULT), indicesString, "bulkApi");

    if (bulkResponse.hasFailures()) {
      var failureCount = 0;
      var versionConflictCount = 0;
      for (var item : bulkResponse.getItems()) {
        if (item.isFailed()) {
          if (item.getFailureMessage().contains("version_conflict_engine_exception")) {
            versionConflictCount++;
          } else {
            failureCount++;
            log.warn("indexResources:: bulk item failure [id: {}, message: {}]",
              item.getId(), item.getFailureMessage());
          }
        }
      }
      if (versionConflictCount > 0) {
        log.info("indexResources:: version conflicts (expected during concurrent writes) [count: {}]",
          versionConflictCount);
      }
      if (failureCount > 0) {
        return getErrorIndexOperationResponse(bulkResponse.buildFailureMessage());
      }
    }

    return getSuccessIndexOperationResponse();
  }

  private IndexRequest prepareIndexRequest(InstanceSearchDocumentBody doc) {
    return new IndexRequest(doc.getTargetIndex())
      .id(doc.getId())
      .routing(doc.getRoutingKey())
      .version(doc.getSourceVersion())
      .versionType(VersionType.EXTERNAL)
      .source(doc.getDocumentBody(), doc.getDataFormat().getXcontentType());
  }

  private DeleteRequest prepareDeleteRequest(InstanceSearchDocumentBody doc) {
    return new DeleteRequest(doc.getTargetIndex())
      .id(doc.getId())
      .routing(doc.getRoutingKey())
      .version(doc.getSourceVersion())
      .versionType(VersionType.EXTERNAL);
  }
}
