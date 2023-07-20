package org.folio.search.service.consortia;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConsortiaTenantProvider implements TenantProvider {

  private final ConsortiaService consortiaService;

  @Override
  public String getTenant(String tenantId) {
    return consortiaService.getCentralTenant(tenantId)
      .orElse(tenantId);
  }
}
