package org.folio.search.model.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;

@Data
@Builder
@RequiredArgsConstructor(staticName = "of")
public class BrowseRequest implements ResourceRequest {

  /**
   * Resource name.
   */
  private final ResourceType resource;

  /**
   * Request tenant id.
   */
  private final String tenantId;

  /**
   * Browse option type.
   */
  private final BrowseOptionType browseOptionType;

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
   * Whether to return only basic properties or entire instance.
   */
  private final Boolean expandAll;

  /**
   * Retrieves the fields listed in the specified parameter.
   */
  private final String include;

  /**
   * Whether to highlight matched resources or not.
   */
  private final Boolean highlightMatch;

  /**
   * Number of preceding records for virtual shelf browsing. Works only when browsing around.
   */
  private final Integer precedingRecordsCount;
}
