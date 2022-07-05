package org.folio.search.model.types;

import static org.folio.search.model.service.CqlResourceIdsRequest.AUTHORITY_ID_PATH;
import static org.folio.search.model.service.CqlResourceIdsRequest.HOLDINGS_ID_PATH;
import static org.folio.search.model.service.CqlResourceIdsRequest.INSTANCE_ID_PATH;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import lombok.Getter;

@Getter
public enum EntityType {

  INSTANCE(INSTANCE_RESOURCE, INSTANCE_ID_PATH),
  AUTHORITY(AUTHORITY_RESOURCE, AUTHORITY_ID_PATH),
  HOLDINGS(INSTANCE_RESOURCE, HOLDINGS_ID_PATH);

  private final String resource;
  private final String sourceIdPath;

  EntityType(String resource, String sourceIdPath) {
    this.resource = resource;
    this.sourceIdPath = sourceIdPath;
  }
}
