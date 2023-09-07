package org.folio.search.service.consortium;

public interface TenantProvider {
  String getTenant(String tenantId);
}
