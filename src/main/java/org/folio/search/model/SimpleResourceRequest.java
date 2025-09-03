package org.folio.search.model;

import org.folio.search.model.types.ResourceType;

public record SimpleResourceRequest(
  ResourceType resource,
  String tenantId
) implements ResourceRequest {

}
