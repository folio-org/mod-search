package org.folio.search.model.service;

import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;

/**
 * CQL based facet request model.
 */
@Data
@RequiredArgsConstructor(staticName = "of")
public class CqlFacetRequest implements ResourceRequest {

  /**
   * Resource name.
   */
  private final ResourceType resource;

  /**
   * Request tenant id.
   */
  private final String tenantId;

  /**
   * A CQL query string with search conditions.
   */
  private final String query;

  /**
   * List of facet names in format {@code {facetName}} or {@code {facetName}:{facetLimit}}.
   */
  private final List<String> facet;
}
