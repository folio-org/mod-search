package org.folio.search.repository;

import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.service.converter.ElasticsearchHitConverter;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Repository
@RequiredArgsConstructor
public class SearchRepository {

  private final RestHighLevelClient elasticsearchClient;
  private final ElasticsearchHitConverter elasticsearchHitConverter;

  /**
   * Executes request to elasticsearch and returns search result with related documents.
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

  private SearchResult mapToSearchResult(SearchResponse response) {
    var hits = response.getHits();
    var searchResult = new SearchResult();
    searchResult.setTotalRecords((int) hits.getTotalHits().value);
    searchResult.setInstances(getResultDocuments(hits.getHits()));
    return searchResult;
  }

  private List<Instance> getResultDocuments(SearchHit[] searchHits) {
    return Arrays.stream(searchHits)
      .map(SearchHit::getSourceAsMap)
      .map(map -> elasticsearchHitConverter.convert(map, Instance.class))
      .collect(Collectors.toList());
  }
}
