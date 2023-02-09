package org.folio.search.repository;

import static java.util.stream.Collectors.joining;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.CommonUtils.listToLogMsg;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public abstract class AbstractResourceRepository implements ResourceRepository {

  protected RestHighLevelClient elasticsearchClient;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documents) {
    log.debug("indexResources:: by [documents: {}]", listToLogMsg(documents, true));

    if (CollectionUtils.isEmpty(documents)) {
      log.info("indexResources:: empty documents");
      return getSuccessIndexOperationResponse();
    }

    var bulkRequest = prepareBulkRequest(documents);
    var bulkApiResponse = executeBulkRequest(bulkRequest);

    if (bulkApiResponse.hasFailures()) {
      log.warn("BulkResponse has failure: {}", bulkApiResponse.buildFailureMessage());
      return getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage());
    }
    return getSuccessIndexOperationResponse();
  }

  @Autowired
  public void setElasticsearchClient(RestHighLevelClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
  }

  protected BulkResponse executeBulkRequest(BulkRequest bulkRequest) {
    var indicesString = bulkRequest.requests().stream().map(DocWriteRequest::index).collect(joining(","));
    return performExceptionalOperation(() -> elasticsearchClient.bulk(bulkRequest, DEFAULT), indicesString, "bulkApi");
  }

  protected static BulkRequest prepareBulkRequest(List<SearchDocumentBody> documents) {
    var request = new BulkRequest();
    for (var document : documents) {
      request.add(document.getAction() == INDEX ? prepareIndexRequest(document) : prepareDeleteRequest(document));
    }
    return request;
  }

  /**
   * Prepares {@link IndexRequest} object from the given {@link SearchDocumentBody} object.
   *
   * @param doc - search document body as {@link SearchDocumentBody} object.
   * @return prepared {@link IndexRequest} request
   */
  protected static IndexRequest prepareIndexRequest(SearchDocumentBody doc) {
    log.info("prepareIndexRequest:: by [document.id: {}, document.index: {}]", doc.getId(), doc.getIndex());

    return new IndexRequest(doc.getIndex())
      .id(doc.getId())
      .source(doc.getDocumentBody(), doc.getDataFormat().getXcontentType());
  }

  /**
   * Prepares {@link DeleteRequest} object from the given {@link SearchDocumentBody} object.
   *
   * @param document - search document body as {@link SearchDocumentBody} object.
   * @return prepared {@link DeleteRequest} request
   */
  protected static DeleteRequest prepareDeleteRequest(SearchDocumentBody document) {
    log.info("prepareDeleteRequest:: by [document.id: {}, document.index: {}]",
      document.getId(), document.getIndex());

    return new DeleteRequest(document.getIndex())
      .id(document.getId());
  }
}
