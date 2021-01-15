package org.folio.search.repository;

import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Repository
@RequiredArgsConstructor
public class SearchRepository {

  private final RestHighLevelClient elasticsearchClient;

  /**
   * Executes request to elasticsearch and returns found documents from elasticsearch.
   *
   * @param queryBuilder elasticsearch query as {@link QueryBuilder} object.
   * @param cqlSearchRequest search request as {@link CqlSearchRequest} object.
   * @return search result as {@link SearchResult} object.
   */
  public SearchResult search(CqlSearchRequest cqlSearchRequest, SearchSourceBuilder queryBuilder) {
    var index = getElasticsearchIndexName(cqlSearchRequest);
    var searchRequest = new SearchRequest()
      .routing(cqlSearchRequest.getTenantId())
      .source(queryBuilder)
      .indices(index);

    return mapToSearchResult(
      performExceptionalOperation(
        () -> elasticsearchClient.search(searchRequest, RequestOptions.DEFAULT),
        index, "searchApi")
    );
  }

  private static SearchResult mapToSearchResult(SearchResponse response) {
    var hits = response.getHits();
    return SearchResult.of(hits.getTotalHits().value, getResultDocuments(hits.getHits()));
  }

  private static List<Map<String, Object>> getResultDocuments(SearchHit[] searchHits) {
    return Arrays.stream(searchHits)
      .map(SearchHit::getSourceAsMap)
      .collect(Collectors.toList());
  }
}
