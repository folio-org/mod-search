package org.folio.search.repository;

import static java.util.stream.Collectors.joining;
import static org.folio.search.model.types.IndexActionType.INDEX;
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
  protected IndexNameProvider indexNameProvider;

  @Override
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> documents) {
    if (CollectionUtils.isEmpty(documents)) {
      return getSuccessIndexOperationResponse();
    }

    var bulkRequest = prepareBulkRequest(documents);
    var bulkApiResponse = executeBulkRequest(bulkRequest);

    return bulkApiResponse.hasFailures()
           ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
           : getSuccessIndexOperationResponse();
  }

  @Autowired
  public void setIndexNameProvider(IndexNameProvider indexNameProvider) {
    this.indexNameProvider = indexNameProvider;
  }

  @Autowired
  public void setElasticsearchClient(RestHighLevelClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
  }

  protected BulkResponse executeBulkRequest(BulkRequest bulkRequest) {
    var indicesString = bulkRequest.requests().stream().map(DocWriteRequest::index).collect(joining(","));
    return performExceptionalOperation(() -> elasticsearchClient.bulk(bulkRequest, DEFAULT), indicesString, "bulkApi");
  }

  protected BulkRequest prepareBulkRequest(List<SearchDocumentBody> documents) {
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
  protected IndexRequest prepareIndexRequest(SearchDocumentBody doc) {
    return new IndexRequest(indexNameProvider.getIndexName(doc))
      .id(doc.getId())
      .source(doc.getDocumentBody(), doc.getDataFormat().getXcontentType());
  }

  /**
   * Prepares {@link DeleteRequest} object from the given {@link SearchDocumentBody} object.
   *
   * @param doc - search document body as {@link SearchDocumentBody} object.
   * @return prepared {@link DeleteRequest} request
   */
  protected DeleteRequest prepareDeleteRequest(SearchDocumentBody doc) {
    return new DeleteRequest(indexNameProvider.getIndexName(doc)).id(doc.getId());
  }
}
