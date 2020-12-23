package org.folio.search.repository;

import org.elasticsearch.index.query.QueryBuilder;
import org.folio.search.model.rest.response.SearchResult;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Repository
public class SearchRepository {

  /**
   * Executes request to elasticsearch and returns found documents from elasticsearch.
   *
   * @param queryBuilder elasticsearch query as {@link QueryBuilder} object.
   * @param tenantId the tenant id as {@link String} object.
   * @return search result as {@link SearchResult} object.
   */
  @SuppressWarnings("unused")
  public SearchResult search(QueryBuilder queryBuilder, String tenantId) {
    return new SearchResult();
  }
}
