package org.folio.search.model.service;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;

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
  private final Class<T> responseClass;

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
   * @param <T>                   - generic type for {@link CqlSearchRequest} object.
   * @param resourceName
   * @param tenantId              - tenant id
   * @param query                 - CQL query
   * @param limit                 - search result records limit
   * @param offset                - search result offset
   * @param expandAll             - whether to return only response properties or entire record
   * @param includeNumberOfTitles - indicates whether the number of titles should be counted.
   * @return created {@link CqlSearchRequest} object
   */
  public static <T> CqlSearchRequest<T> of(String resourceName, Class<T> responseClass, String tenantId, String query,
                                           Integer limit, Integer offset, Boolean expandAll,
                                           Boolean includeNumberOfTitles) {
    return new CqlSearchRequest<>(resourceName, responseClass, tenantId, query, limit, offset, expandAll,
      includeNumberOfTitles);
  }

  public static <T> CqlSearchRequest<T> of(String resourceName, Class<T> responseClass, String tenantId, String query,
                                           Integer limit, Integer offset, Boolean expandAll) {
    return CqlSearchRequest.of(resourceName, responseClass, tenantId, query, limit, offset, expandAll, true
    );
  }
}
