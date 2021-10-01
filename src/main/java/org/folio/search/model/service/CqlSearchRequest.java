package org.folio.search.model.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;

/**
 * CQL based search request model.
 */
@Data
@RequiredArgsConstructor(staticName = "of")
public class CqlSearchRequest implements ResourceRequest {

  /**
   * Resource name.
   */
  private final String resource;

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
   * Whether to return only basic properties or entire instance.
   */
  private final Boolean expandAll;
}
