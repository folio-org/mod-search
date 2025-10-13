package org.folio.search.repository;

import static org.folio.search.configuration.SearchCacheNames.ES_INDICES_CACHE;
import static org.folio.search.utils.SearchResponseHelper.getErrorFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.common.xcontent.XContentType.JSON;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.configuration.properties.IndexManagementConfigurationProperties;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.PutMappingRequest;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to create/modify/update index settings and mappings.
 */
@Log4j2
@Repository
@RequiredArgsConstructor
public class IndexRepository {

  private final RestHighLevelClient elasticsearchClient;
  private final IndexManagementConfigurationProperties indexManagementConfig;

  /**
   * Creates index using passed settings and mappings JSONs.
   *
   * @param index    index name as {@link String} object
   * @param settings settings JSON {@link String} object
   * @param mappings mappings JSON {@link String} object
   * @return {@link FolioCreateIndexResponse} object
   */
  @CacheEvict(cacheNames = ES_INDICES_CACHE, key = "#index")
  public FolioCreateIndexResponse createIndex(String index, String settings, String mappings) {
    var createIndexRequest = new CreateIndexRequest(index)
      .settings(settings, JSON)
      .mapping(mappings, JSON);

    var createIndexResponse = performExceptionalOperation(
      () -> elasticsearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT),
      index, "createIndexApi");

    return createIndexResponse.isAcknowledged()
      ? getSuccessFolioCreateIndexResponse(List.of(index))
      : getErrorFolioCreateIndexResponse(List.of(index));
  }

  /**
   * Update index settings {@link UpdateSettingsRequest}.
   *
   * @param index    index name as {@link String} object
   * @param settings settings JSON {@link String} object
   * @return {@link FolioCreateIndexResponse} object
   */
  public FolioIndexOperationResponse updateIndexSettings(String index, String settings) {
    var updateSettingsRequest = new UpdateSettingsRequest(index)
      .settings(settings, JSON);

    var updateIndexSettingsResponse = performExceptionalOperation(
      () -> elasticsearchClient.indices().putSettings(updateSettingsRequest, RequestOptions.DEFAULT),
      index, "putIndexSettingsApi");

    return updateIndexSettingsResponse.isAcknowledged()
      ? getSuccessIndexOperationResponse()
      : getErrorIndexOperationResponse("Failed to put settings");
  }

  /**
   * Executes {@link PutMappingRequest} for passed index and mappings JSON.
   *
   * @param index    index name as {@link String} object
   * @param mappings mappings JSON {@link String} object
   * @return {@link FolioCreateIndexResponse} object
   */
  public FolioIndexOperationResponse updateMappings(String index, String mappings) {
    var putMappingRequest = new PutMappingRequest(index).source(mappings, JSON);
    var putMappingsResponse = performExceptionalOperation(
      () -> elasticsearchClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT),
      index, "putMappingsApi");

    return putMappingsResponse.isAcknowledged()
      ? getSuccessIndexOperationResponse()
      : getErrorIndexOperationResponse("Failed to put mappings");
  }

  /**
   * Checks if index exists in elasticsearch by name.
   *
   * @param index elasticsearch index name
   * @return true if index exists, false - otherwise
   */
  @Cacheable(value = ES_INDICES_CACHE, key = "#index", unless = "#result == false")
  public boolean indexExists(String index) {
    var request = new GetIndexRequest(index);
    return performExceptionalOperation(
      () -> elasticsearchClient.indices().exists(request, RequestOptions.DEFAULT),
      index, "indexExists");
  }

  /**
   * Refreshes the Elasticsearch indices.
   *
   * @param indices - Elasticsearch index names as array of {@link String} objects.
   */
  public void refreshIndices(String... indices) {
    performExceptionalOperation(
      () -> elasticsearchClient.indices().refresh(new RefreshRequest(indices), DEFAULT),
      String.join(",", indices), "refreshApi");
  }

  /**
   * Deletes documents from an index by tenant ID, optionally preserving shared documents.
   * This method uses OpenSearch delete-by-query to remove tenant-specific documents without
   * dropping the entire index, enabling preservation of shared consortium data.
   *
   * @param indexName index name as {@link String} object
   * @param tenantId tenant id as {@link String} object
   * @param preserveShared if true, preserves documents marked as shared (shared=true)
   * @return {@link FolioIndexOperationResponse} object indicating success or failure
   */
  public FolioIndexOperationResponse deleteDocumentsByTenantId(String indexName, String tenantId,
                                                               boolean preserveShared) {
    log.debug("deleteDocumentsByTenantId:: by [index: {}, tenantId: {}, preserveShared: {}]",
      indexName, tenantId, preserveShared);

    if (!indexExists(indexName)) {
      log.debug("deleteDocumentsByTenantId:: index does not exist [index: {}]", indexName);
      return getSuccessIndexOperationResponse();
    }

    var deleteByQueryRequest = new DeleteByQueryRequest(indexName);

    // Configure performance settings
    deleteByQueryRequest.setBatchSize(indexManagementConfig.getDeleteQueryBatchSize());
    deleteByQueryRequest.setScroll(
      TimeValue.timeValueMinutes(indexManagementConfig.getDeleteQueryScrollTimeoutMinutes()));
    deleteByQueryRequest.setTimeout(
      TimeValue.timeValueMinutes(indexManagementConfig.getDeleteQueryRequestTimeoutMinutes()));
    deleteByQueryRequest.setRefresh(indexManagementConfig.getDeleteQueryRefresh());

    // Build query: tenantId = targetTenant AND (NOT shared = true IF preserveShared)
    //todo: tenantId is on a top level only for instances
    if (preserveShared) {
      var query = boolQuery()
        .must(termQuery(TENANT_ID_FIELD_NAME, tenantId))
        .mustNot(termQuery(SHARED_FIELD_NAME, true));
      deleteByQueryRequest.setQuery(query);
      log.info("deleteDocumentsByTenantId:: deleting tenant documents preserving shared "
        + "[index: {}, tenantId: {}]", indexName, tenantId);
    } else {
      deleteByQueryRequest.setQuery(termQuery(TENANT_ID_FIELD_NAME, tenantId));
      log.info("deleteDocumentsByTenantId:: deleting all tenant documents "
        + "[index: {}, tenantId: {}]", indexName, tenantId);
    }

    var bulkByScrollResponse = performExceptionalOperation(
      () -> elasticsearchClient.deleteByQuery(deleteByQueryRequest, DEFAULT),
      indexName, "deleteByQueryApi");

    var deletedCount = bulkByScrollResponse.getDeleted();
    log.info("deleteDocumentsByTenantId:: completed [index: {}, tenantId: {}, deleted: {}, "
      + "preserveShared: {}]", indexName, tenantId, deletedCount, preserveShared);

    return bulkByScrollResponse.getBulkFailures().isEmpty()
      ? getSuccessIndexOperationResponse()
      : getErrorIndexOperationResponse(getBulkByScrollResponseErrorMessage(bulkByScrollResponse));
  }

  /**
   * Deletes elasticsearch index by name.
   *
   * @param index elasticsearch index name
   */
  @CacheEvict(cacheNames = ES_INDICES_CACHE, key = "#index")
  public void dropIndex(String index) {
    var request = new DeleteIndexRequest(index);

    performExceptionalOperation(() -> elasticsearchClient.indices()
      .delete(request, RequestOptions.DEFAULT), index, "dropIndex");
  }

  /**
   * Extracts error messages from BulkByScrollResponse failures.
   */
  private static String getBulkByScrollResponseErrorMessage(BulkByScrollResponse bulkByScrollResponse) {
    return bulkByScrollResponse.getBulkFailures()
      .stream().map(BulkItemResponse.Failure::getMessage)
      .collect(Collectors.joining(","));
  }
}
