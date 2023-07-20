package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConsortiaTenantProvider implements TenantProvider {

  private final ConsortiaTenantService consortiaTenantService;

  @Override
  public String getTenant(String tenantId) {
    return consortiaTenantService.getCentralTenant(tenantId)
      .orElse(tenantId);
  }
}
