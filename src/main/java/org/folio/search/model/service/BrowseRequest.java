package org.folio.search.model.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;

@Data
@Builder
@RequiredArgsConstructor(staticName = "of")
public class BrowseRequest implements ResourceRequest {

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
   * Target field for browsing.
   */
  private final String targetField;

  /**
   * Sub-field for browsing.
   */
  private final String subField;

  /**
   * Whether to return only basic properties or entire instance.
   */
  private final Boolean expandAll;

  /**
   * Whether to highlight matched resources or not.
   */
  private final Boolean highlightMatch;

  /**
   * Number of preceding records for virtual shelf browsing. Works only when browsing around.
   */
  private final Integer precedingRecordsCount;
}
