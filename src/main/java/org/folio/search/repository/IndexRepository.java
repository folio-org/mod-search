package org.folio.search.repository;

import static org.folio.search.configuration.SearchCacheNames.ES_INDICES_CACHE;
import static org.folio.search.utils.SearchResponseHelper.getErrorFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.common.xcontent.XContentType.JSON;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.PutMappingRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to create/modify/update index settings and mappings.
 */
@Repository
@RequiredArgsConstructor
public class IndexRepository {

  private final RestHighLevelClient elasticsearchClient;

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
}
