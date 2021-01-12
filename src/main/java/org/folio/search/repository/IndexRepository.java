package org.folio.search.repository;

import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.LinkedHashSet;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.folio.search.model.SearchDocumentBody;
import org.folio.search.model.rest.response.FolioCreateIndexResponse;
import org.folio.search.model.rest.response.FolioIndexResourceResponse;
import org.folio.search.model.rest.response.FolioPutMappingResponse;
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
      .settings(settings, XContentType.JSON)
      .mapping(mappings, XContentType.JSON);

    var createIndexResponse = performExceptionalOperation(
      () -> elasticsearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT),
      index, "createIndexApi");

    return createIndexResponse.isAcknowledged()
      ? FolioCreateIndexResponse.success(List.of(index))
      : FolioCreateIndexResponse.error("error", List.of(index));
  }

  /**
   * Executes {@link PutMappingRequest} for passed index and mappings JSON.
   *
   * @param index index name as {@link String} object
   * @param mappings mappings JSON {@link String} object
   * @return {@link FolioCreateIndexResponse} object
   */
  public FolioPutMappingResponse updateMappings(String index, String mappings) {
    var putMappingRequest = new PutMappingRequest(index).source(mappings, XContentType.JSON);
    var putMappingsResponse = performExceptionalOperation(
      () -> elasticsearchClient.indices().putMapping(putMappingRequest, RequestOptions.DEFAULT),
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
      () -> elasticsearchClient.bulk(bulkRequest, RequestOptions.DEFAULT),
      String.join(",", indices), "bulkApi");

    return bulkApiResponse.hasFailures()
      ? FolioIndexResourceResponse.error(bulkApiResponse.buildFailureMessage())
      : FolioIndexResourceResponse.success();
  }


  private static IndexRequest prepareIndexRequest(String index, SearchDocumentBody body) {
    return new IndexRequest(index)
      .id(body.getId())
      .routing(body.getRouting())
      .source(body.getRawJson(), XContentType.JSON);
  }
}
