package org.folio.search.service;

import static org.apache.commons.lang3.BooleanUtils.isFalse;

import lombok.RequiredArgsConstructor;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.model.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.folio.search.service.converter.ElasticsearchDocumentConverter;
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
  private final ElasticsearchDocumentConverter documentConverter;

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
    return documentConverter.convertToSearchResult(searchResponse, request.getResourceClass());
  }
}
