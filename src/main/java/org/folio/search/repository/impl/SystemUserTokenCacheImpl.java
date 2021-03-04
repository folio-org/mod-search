package org.folio.search.repository.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.folio.search.repository.SystemUserTokenCache;
import org.springframework.stereotype.Component;

@Component
public class SystemUserTokenCacheImpl implements SystemUserTokenCache {
  private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

  @Override
  public String getTokenByTenant(String tenantId) {
    return TOKENS.get(tenantId);
  }

  @Override
  public boolean hasTokenForTenant(String tenantId) {
    return TOKENS.containsKey(tenantId);
  }

  @Override
  public void save(String tenantId, String token) {
    TOKENS.put(tenantId, token);
  }
}
