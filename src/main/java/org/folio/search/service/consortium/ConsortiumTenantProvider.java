package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConsortiumTenantProvider implements TenantProvider {

  private final ConsortiumTenantService consortiumTenantService;

  @Override
  public String getTenant(String tenantId) {
    return consortiumTenantService.getCentralTenant(tenantId)
      .orElse(tenantId);
  }
}
