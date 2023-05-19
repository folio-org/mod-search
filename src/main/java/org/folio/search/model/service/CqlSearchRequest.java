package org.folio.search.model.service;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;
import org.folio.search.utils.SearchUtils;

/**
 * CQL based search request model.
 */
@Data
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class CqlSearchRequest<T> implements ResourceRequest {

  /**
   * Resource name.
   */
  private final String resource;

  /**
   * Resource class for response.
   */
  private final Class<T> resourceClass;

  /**
   * Request tenant id.
   */
  private final String tenantId;

  /**
   * A CQL query string with search conditions.
   */
  private final String query;

  /**
   * Limit the number of elements in the response.
   */
  private final Integer limit;

  /**
   * Skip over a number of elements by specifying an offset value for the query.
   */
  private final Integer offset;

  /**
   * Whether to return only basic properties or entire record.
   */
  private final Boolean expandAll;

  /**
   * Whether to return only basic properties or entire record.
   */
  private final Boolean includeNumberOfTitles;

  /**
   * Creates {@link CqlSearchRequest} object for given variables.
   *
   * @param resourceClass         -  resource class
   * @param tenantId              - tenant id
   * @param query                 - CQL query
   * @param limit                 - search result records limit
   * @param offset                - search result offset
   * @param expandAll             - whether to return only response properties or entire record
   * @param <R>                   - generic type for {@link CqlSearchRequest} object.
   * @param includeNumberOfTitles - indicates whether the number of titles should be counted.
   * @return created {@link CqlSearchRequest} object
   */
  public static <R> CqlSearchRequest<R> of(Class<R> resourceClass, String tenantId, String query,
                                           Integer limit, Integer offset, Boolean expandAll,
                                           Boolean includeNumberOfTitles) {
    var resource = SearchUtils.getResourceName(resourceClass);
    return new CqlSearchRequest<>(resource, resourceClass, tenantId, query, limit, offset, expandAll,
      includeNumberOfTitles);
  }

  public static <R> CqlSearchRequest<R> of(Class<R> resourceClass, String tenantId, String query,
                                           Integer limit, Integer offset, Boolean expandAll) {
    return CqlSearchRequest.of(resourceClass, tenantId, query, limit, offset, expandAll, true);
  }
}
