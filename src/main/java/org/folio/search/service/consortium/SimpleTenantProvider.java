package org.folio.search.service.consortium;

import org.springframework.stereotype.Component;

/**
 * A simple {@link TenantProvider} implementation that returns the tenant id as-is,
 * without any consortium lookup. Used during tenant initialization to avoid
 * consortium-related caching and executor side-effects.
 */
@Component
public class SimpleTenantProvider implements TenantProvider {

  @Override
  public String getTenant(String tenantId) {
    return tenantId;
  }
}

