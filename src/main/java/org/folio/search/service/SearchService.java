package org.folio.search.service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchHitConverter;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.springframework.stereotype.Service;

/**
 * Search service with set of operation to perform search operations.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

  private final SearchRepository searchRepository;
  private final SearchFieldProvider searchFieldProvider;
  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final ElasticsearchHitConverter elasticsearchHitConverter;

  /**
   * Prepares search query and executes search request to the search engine.
   *
   * @param request cql search request as {@link CqlSearchRequest} object
   * @return search result.
   */
  public <T> SearchResult<T> search(CqlSearchRequest<T> request) {
    var resource = request.getResource();
    var queryBuilder = cqlSearchQueryConverter.convert(request.getQuery(), resource)
      .from(request.getOffset())
      .size(request.getLimit())
      .trackTotalHits(true);

    if (isFalse(request.getExpandAll())) {
      var includes = searchFieldProvider.getSourceFields(resource).toArray(String[]::new);
      queryBuilder.fetchSource(includes, null);
    }

    var searchResponse = searchRepository.search(request, queryBuilder);
    return mapToSearchResult(searchResponse, request.getResourceClass());
  }

  private <T> SearchResult<T> mapToSearchResult(SearchResponse response, Class<T> responseClass) {
    return Optional.ofNullable(response)
      .map(SearchResponse::getHits)
      .map(searchHits -> mapToSearchResult(searchHits, responseClass))
      .orElseThrow(() -> new SearchServiceException(String.format(
        "Failed to parse search response object [response: %s]", response)));
  }

  private <T> SearchResult<T> mapToSearchResult(SearchHits hits, Class<T> responseClass) {
    var totalHits = hits.getTotalHits();
    var totalRecords = totalHits != null ? totalHits.value : 0L;
    return SearchResult.of((int) totalRecords, getResultDocuments(hits.getHits(), responseClass));
  }

  private <T> List<T> getResultDocuments(SearchHit[] searchHits, Class<T> responseClass) {
    return Arrays.stream(searchHits)
      .map(SearchHit::getSourceAsMap)
      .map(map -> elasticsearchHitConverter.convert(map, responseClass))
      .collect(Collectors.toList());
  }
}
