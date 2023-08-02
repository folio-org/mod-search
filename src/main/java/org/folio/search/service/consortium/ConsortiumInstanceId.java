package org.folio.search.service.consortium;

public record ConsortiumInstanceId(String tenantId, String instanceId) {

  @Override
  public String toString() {
    return tenantId + "-" + instanceId;
  }
}
