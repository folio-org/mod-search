package org.folio.search.repository;

import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.ResourceRequest;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Repository
@RequiredArgsConstructor
public class SearchRepository {

  private final RestHighLevelClient elasticsearchClient;

  /**
   * Executes request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSource elasticsearch search source as {@link SearchSourceBuilder} object.
   * @return search result as {@link SearchResult} object.
   */
  public SearchResponse search(ResourceRequest resourceRequest, SearchSourceBuilder searchSource) {
    var index = getElasticsearchIndexName(resourceRequest);
    var searchRequest = new SearchRequest()
      .routing(resourceRequest.getTenantId())
      .source(searchSource)
      .indices(index);

    return performExceptionalOperation(
      () -> elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT),
      index, "searchApi");
  }
}
