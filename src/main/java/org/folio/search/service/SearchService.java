package org.folio.search.service;

import lombok.RequiredArgsConstructor;
import org.folio.search.cql.CqlSearchQueryConverter;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.service.CqlSearchRequest;
import org.folio.search.repository.SearchRepository;
import org.springframework.stereotype.Service;

/**
 * Search service with set of operation to perform search operations.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

  private final CqlSearchQueryConverter cqlSearchQueryConverter;
  private final SearchRepository searchRepository;

  /**
   * Prepares search query and executes search request to the search engine.
   *
   * @param searchRequest cql search request as {@link CqlSearchRequest} object
   * @return search result with founded data.
   */
  public SearchResult search(CqlSearchRequest searchRequest) {
    return searchRepository.search(searchRequest, cqlSearchQueryConverter.convert(searchRequest));
  }
}
