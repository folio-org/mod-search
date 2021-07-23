package org.folio.search.repository;

import static org.elasticsearch.common.xcontent.XContentType.JSON;
import static org.folio.search.configuration.CacheConfiguration.ES_INDICES_CACHE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchResponseHelper.getErrorFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.folio.search.domain.dto.FolioCreateIndexResponse;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.model.SearchDocumentBody;
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

  /**
   * Creates index using passed settings and mappings JSONs.
   *
   * @param index index name as {@link String} object
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
   * @param index index name as {@link String} object
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
   * Saves provided list of {@link SearchDocumentBody} objects to elasticsearch.
   *
   * @param esDocumentBodies list wth {@link SearchDocumentBody} object
   */
  public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    if (CollectionUtils.isEmpty(esDocumentBodies)) {
      return getSuccessIndexOperationResponse();
    }

    var bulkRequest = new BulkRequest();
    var indices = new LinkedHashSet<String>();
    for (var body : esDocumentBodies) {
      indices.add(body.getIndex());
      bulkRequest.add(body.getAction() == INDEX ? prepareIndexRequest(body) : prepareDeleteRequest(body));
    }

    var bulkApiResponse = performExceptionalOperation(
      () -> elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT),
      String.join(",", indices), "bulkApi");

    return bulkApiResponse.hasFailures()
      ? getErrorIndexOperationResponse(bulkApiResponse.buildFailureMessage())
      : getSuccessIndexOperationResponse();
  }

  /**
   * Checks if index exists in elasticsearch by name.
   *
   * @param index elasticsearch index name
   * @return true if index exists, false - otherwise
   */
  @Cacheable(value = ES_INDICES_CACHE, key = "#index", unless = "#result == false")
  public boolean indexExists(String index) {
    log.info("Checking that index exists [index: {}]", index);
    var request = new GetIndexRequest(index);
    return performExceptionalOperation(() ->
      elasticsearchClient.indices().exists(request, RequestOptions.DEFAULT),
      index, "indexExists");
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

  private static IndexRequest prepareIndexRequest(SearchDocumentBody body) {
    return new IndexRequest(body.getIndex())
      .id(body.getId())
      .routing(body.getRouting())
      .source(body.getRawJson(), JSON);
  }

  private static DeleteRequest prepareDeleteRequest(SearchDocumentBody event) {
    return new DeleteRequest(event.getIndex())
      .id(event.getId()).routing(event.getRouting());
  }
}
