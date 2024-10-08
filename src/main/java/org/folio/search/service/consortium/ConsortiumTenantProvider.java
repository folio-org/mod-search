package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsortiumTenantProvider implements TenantProvider {

  private final ConsortiumTenantService consortiumTenantService;

  @Override
  public String getTenant(String tenantId) {
    return consortiumTenantService.getCentralTenant(tenantId)
      .orElse(tenantId);
  }

  public boolean isMemberTenant(String tenantId) {
    return consortiumTenantService.getCentralTenant(tenantId)
      .map(centralTenantId -> !centralTenantId.equals(tenantId))
      .orElse(false);
  }

  public boolean isCentralTenant(String tenantId) {
    return consortiumTenantService.getCentralTenant(tenantId)
      .map(centralTenantId -> centralTenantId.equals(tenantId))
      .orElse(false);
  }

  public boolean isConsortiumTenant(String tenantId) {
    return consortiumTenantService.getCentralTenant(tenantId).isPresent();
  }
}
