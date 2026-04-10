package org.folio.search.repository;

import static java.util.stream.Collectors.joining;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.stereotype.Repository;

/**
 * Repository for indexing V1 nested instance documents to a specific target index.
 * Uses standard versioning (last-write-wins), not external versioning.
 */
@Log4j2
@Repository
@RequiredArgsConstructor
public class NestedInstanceResourceRepository {

  private final RestHighLevelClient elasticsearchClient;

  public void indexResources(List<SearchDocumentBody> documents, String targetIndex) {
    if (CollectionUtils.isEmpty(documents)) {
      return;
    }

    var bulkRequest = new BulkRequest();
    for (var doc : documents) {
      if (doc.getAction() == IndexActionType.INDEX) {
        bulkRequest.add(new IndexRequest(targetIndex)
          .id(doc.getId())
          .source(doc.getDocumentBody(), doc.getDataFormat().getXcontentType()));
      } else {
        bulkRequest.add(new DeleteRequest(targetIndex).id(doc.getId()));
      }
    }

    var indicesString = bulkRequest.requests().stream().map(DocWriteRequest::index).collect(joining(","));
    var bulkResponse = performExceptionalOperation(
      () -> elasticsearchClient.bulk(bulkRequest, DEFAULT), indicesString, "bulkApi");

    if (bulkResponse.hasFailures()) {
      log.warn("indexResources:: bulk failures [message: {}]", bulkResponse.buildFailureMessage());
    }
  }

  public void deleteById(String id, String targetIndex) {
    var deleteRequest = new DeleteRequest(targetIndex).id(id);
    performExceptionalOperation(
      () -> elasticsearchClient.delete(deleteRequest, DEFAULT), targetIndex, "deleteApi");
  }
}
