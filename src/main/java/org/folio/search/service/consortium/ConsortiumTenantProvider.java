package org.folio.search.service.consortium;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConsortiumTenantProvider implements TenantProvider {

  private final UserTenantsService userTenantsService;

  @Override
  public String getTenant(String tenantId) {
    return userTenantsService.getCentralTenant(tenantId)
      .orElse(tenantId);
  }
}
