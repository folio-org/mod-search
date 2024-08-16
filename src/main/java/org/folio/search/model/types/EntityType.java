package org.folio.search.model.types;

import static org.folio.search.model.service.CqlResourceIdsRequest.AUTHORITY_ID_PATH;
import static org.folio.search.model.service.CqlResourceIdsRequest.HOLDINGS_ID_PATH;
import static org.folio.search.model.service.CqlResourceIdsRequest.INSTANCE_ID_PATH;

import lombok.Getter;

@Getter
public enum EntityType {

  INSTANCE(ResourceType.INSTANCE, INSTANCE_ID_PATH),
  AUTHORITY(ResourceType.AUTHORITY, AUTHORITY_ID_PATH),
  HOLDINGS(ResourceType.INSTANCE, HOLDINGS_ID_PATH);

  private final ResourceType resource;
  private final String sourceIdPath;

  EntityType(ResourceType resource, String sourceIdPath) {
    this.resource = resource;
    this.sourceIdPath = sourceIdPath;
  }
}
