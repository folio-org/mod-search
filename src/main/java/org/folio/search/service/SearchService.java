package org.folio.search.service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.service.CqlSearchServiceRequest;
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
   * @param request cql search request as {@link CqlSearchServiceRequest} object
   * @return search result.
   */
  public SearchResult search(CqlSearchServiceRequest request) {
    var resource = request.getResource();
    var queryBuilder = cqlSearchQueryConverter.convert(request.getQuery(), resource)
      .from(request.getOffset())
      .size(request.getLimit())
      .trackTotalHits(true);

    if (!request.getExpandAll()) {
      var includes = searchFieldProvider.getSourceFields(resource).toArray(String[]::new);
      queryBuilder.fetchSource(includes, null);
    }

    return mapToSearchResult(searchRepository.search(request, queryBuilder));
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
