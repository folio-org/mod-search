package org.folio.search.model.service;

import org.folio.search.model.ResourceRequest;
import org.folio.search.model.types.ResourceType;

/**
 * CQL based resource ids request model.
 *
 * @param resource        Resource name.
 * @param tenantId        Request tenant id.
 * @param query           A CQL query string with search conditions.
 * @param sourceFieldPath Field path to the id field of the document.
 */
public record CqlResourceIdsRequest(
  ResourceType resource,
  String tenantId,
  String query,
  String sourceFieldPath
) implements ResourceRequest {

  public static final String INSTANCE_ID_PATH = "id";
  public static final String AUTHORITY_ID_PATH = "id";
  public static final String HOLDINGS_ID_PATH = "holdings.id";
}
