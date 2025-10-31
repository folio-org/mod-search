package org.folio.search.repository;

import static java.util.stream.Collectors.joining;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.configuration.properties.IndexManagementConfigurationProperties;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.ResourceType;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;

@Log4j2
public abstract class AbstractResourceRepository implements ResourceRepository {

  protected RestHighLevelClient elasticsearchClient;
  protected IndexNameProvider indexNameProvider;
  protected IndexManagementConfigurationProperties indexManagementConfig;

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

  @Override
  public FolioIndexOperationResponse deleteResourceByTenantId(ResourceType resource, String tenantId) {
    var indexName = indexNameProvider.getIndexName(resource, tenantId);
    var request = new DeleteByQueryRequest(indexName);
    request.setQuery(termQuery(TENANT_ID_FIELD_NAME, tenantId));
    var bulkByScrollResponse =
      performExceptionalOperation(() -> elasticsearchClient.deleteByQuery(request, DEFAULT), indexName,
        "deleteByQueryApi");
    return bulkByScrollResponse.getBulkFailures().isEmpty()
           ? getSuccessIndexOperationResponse()
           : getErrorIndexOperationResponse(getBulkByScrollResponseErrorMessage(bulkByScrollResponse));
  }

  /**
   * Deletes documents from an index by tenant ID, preserving shared documents.
   * This method uses OpenSearch delete-by-query to remove tenant-specific documents without
   * dropping the entire index, enabling preservation of shared consortium data.
   *
   * @param resourceType resource type as {@link ResourceType} object
   * @param tenantId tenant id as {@link String} object
   * @return {@link FolioIndexOperationResponse} object indicating success or failure
   */
  public FolioIndexOperationResponse deleteConsortiumDocumentsByTenantId(ResourceType resourceType, String tenantId) {
    var indexName = indexNameProvider.getIndexName(resourceType, tenantId);
    log.debug("deleteConsortiumDocumentsByTenantId:: by [indexName: {}, tenantId: {}]", indexName, tenantId);

    var deleteByQueryRequest = new DeleteByQueryRequest(indexName);

    // Configure performance settings
    deleteByQueryRequest.setBatchSize(indexManagementConfig.getDeleteQueryBatchSize());
    deleteByQueryRequest.setScroll(
      TimeValue.timeValueMinutes(indexManagementConfig.getDeleteQueryScrollTimeoutMinutes()));
    deleteByQueryRequest.setTimeout(
      TimeValue.timeValueMinutes(indexManagementConfig.getDeleteQueryRequestTimeoutMinutes()));
    deleteByQueryRequest.setRefresh(indexManagementConfig.getDeleteQueryRefresh());

    // Build query: tenantId = targetTenant AND (NOT shared = true)
    var query = boolQuery()
      .must(termQuery(TENANT_ID_FIELD_NAME, tenantId))
      .mustNot(termQuery(SHARED_FIELD_NAME, true));
    deleteByQueryRequest.setQuery(query);
    log.info("deleteDocumentsByTenantId:: deleting tenant documents preserving shared "
      + "[index: {}, tenantId: {}]", indexName, tenantId);

    var bulkByScrollResponse = performExceptionalOperation(
      () -> elasticsearchClient.deleteByQuery(deleteByQueryRequest, DEFAULT),
      indexName, "deleteByQueryApi");

    var deletedCount = bulkByScrollResponse.getDeleted();
    log.info("deleteDocumentsByTenantId:: completed [index: {}, tenantId: {}, deleted: {}]",
      indexName, tenantId, deletedCount);

    return bulkByScrollResponse.getBulkFailures().isEmpty()
      ? getSuccessIndexOperationResponse()
      : getErrorIndexOperationResponse(getBulkByScrollResponseErrorMessage(bulkByScrollResponse));
  }

  @Autowired
  public void setIndexNameProvider(IndexNameProvider indexNameProvider) {
    this.indexNameProvider = indexNameProvider;
  }

  @Autowired
  public void setElasticsearchClient(RestHighLevelClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
  }

  @Autowired
  public void setIndexManagementConfig(IndexManagementConfigurationProperties indexManagementConfig) {
    this.indexManagementConfig = indexManagementConfig;
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

  private static String getBulkByScrollResponseErrorMessage(BulkByScrollResponse bulkByScrollResponse) {
    return bulkByScrollResponse.getBulkFailures()
      .stream().map(BulkItemResponse.Failure::getMessage)
      .collect(joining(","));
  }
}
