package org.folio.search.repository;

import static java.util.Collections.singletonList;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.elasticsearch.common.xcontent.XContentType.JSON;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.folio.search.model.rest.response.FolioCreateIndexResponse;
import org.folio.search.model.rest.response.FolioIndexResourceResponse;
import org.folio.search.model.rest.response.FolioPutMappingResponse;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.SearchDocumentBody;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to create/modify/update index settings and
 * mappings.
 */
@Repository
public class IndexRepository {

  private final RestHighLevelClient elasticsearchClient;

  /**
   * Constructor that will be used by dependency injection framework.
   *
   * @param elasticsearchClient {@link RestHighLevelClient} component from DI context
   */
  public IndexRepository(RestHighLevelClient elasticsearchClient) {
    this.elasticsearchClient = elasticsearchClient;
  }

  /**
   * Creates index using passed settings and mappings JSONs.
   *
   * @param index index name as {@link String} object
   * @param settings settings JSON {@link String} object
   * @param mappings mappings JSON {@link String} object
   * @return {@link FolioCreateIndexResponse} object
   */
  public FolioCreateIndexResponse createIndex(String index, String settings, String mappings) {
    var createIndexRequest = new CreateIndexRequest(index)
        .settings(settings, JSON)
        .mapping(mappings, JSON);

    var createIndexResponse = performExceptionalOperation(
        () -> elasticsearchClient.indices().create(createIndexRequest, DEFAULT),
        index, "createIndexApi");

    return createIndexResponse.isAcknowledged()
        ? FolioCreateIndexResponse.success(singletonList(index))
        : FolioCreateIndexResponse.error("error", singletonList(index));
  }

  /**
   * Executes {@link PutMappingRequest} for passed index and mappings JSON.
   *
   * @param index index name as {@link String} object
   * @param mappings mappings JSON {@link String} object
   * @return {@link FolioCreateIndexResponse} object
   */
  public FolioPutMappingResponse updateMappings(String index, String mappings) {
    var putMappingRequest = new PutMappingRequest(index).source(mappings, JSON);
    var putMappingsResponse = performExceptionalOperation(
        () -> elasticsearchClient.indices().putMapping(putMappingRequest, DEFAULT),
        index, "putMappingsApi");

    return putMappingsResponse.isAcknowledged()
        ? FolioPutMappingResponse.success()
        : FolioPutMappingResponse.error("Failed to put mappings");
  }

  /**
   * Saves provided list of {@link SearchDocumentBody} objects to elasticsearch.
   *
   * @param esDocumentBodies list wth {@link SearchDocumentBody} object
   */
  public FolioIndexResourceResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
    if (CollectionUtils.isEmpty(esDocumentBodies)) {
      return FolioIndexResourceResponse.success();
    }

    var bulkRequest = new BulkRequest();
    var indices = new LinkedHashSet<String>();
    for (var documentBody : esDocumentBodies) {
      var documentIndex = documentBody.getIndex();
      indices.add(documentIndex);
      bulkRequest.add(prepareIndexRequest(documentIndex, documentBody));
    }

    var bulkApiResponse = performExceptionalOperation(
        () -> elasticsearchClient.bulk(bulkRequest, DEFAULT),
        String.join(",", indices), "bulkApi");

    return bulkApiResponse.hasFailures()
        ? FolioIndexResourceResponse.error(bulkApiResponse.buildFailureMessage())
        : FolioIndexResourceResponse.success();
  }

  private static IndexRequest prepareIndexRequest(String index, SearchDocumentBody body) {
    return new IndexRequest(index)
        .id(body.getId())
        .routing(body.getRouting())
        .source(body.getRawJson(), JSON);
  }

  private static <T> T performExceptionalOperation(Callable<T> func, String index, String type) {
    try {
      return func.call();
    } catch (Exception e) {
      throw new SearchServiceException(String.format(
          "Failed to perform elasticsearch request [index=%s, type=%s, message: %s]",
          index, type, e.getMessage()), e);
    }
  }
}
