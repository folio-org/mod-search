package org.folio.search.model.service;

import java.util.List;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;

/**
 * CQL based facet request model.
 *
 * @param resource Resource name.
 * @param tenantId Request tenant id.
 * @param query    A CQL query string with search conditions.
 * @param facet    List of facet names in format {@code {facetName}} or {@code {facetName}:{facetLimit}}.
 */
public record CqlFacetRequest(
  ResourceType resource,
  String tenantId,
  String query,
  List<String> facet
) implements ResourceRequest { }
