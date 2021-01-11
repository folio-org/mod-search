package org.folio.search.service;

import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

import lombok.RequiredArgsConstructor;
import org.folio.search.model.rest.response.SearchResult;
import org.folio.search.repository.SearchRepository;
import org.springframework.stereotype.Service;

/**
 * Search service with set of operation to perform search operations.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

  private final SearchRepository searchRepository;

  /**
   * Prepares search query and executes search request to the search engine.
   *
   * @param query CQL query as {@link String} object
   * @param tenantId the tenant id as {@link String} object
   * @return search result with found data.
   */
  public SearchResult search(@SuppressWarnings("unused") String query, String tenantId) {
    return searchRepository.search(matchAllQuery(), tenantId);
  }
}
