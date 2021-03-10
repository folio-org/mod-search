package org.folio.search.repository.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserTokenCache;
import org.springframework.stereotype.Component;

@Component
public class SystemUserTokenCacheImpl implements SystemUserTokenCache {
  private static final Map<String, SystemUser> TOKENS = new ConcurrentHashMap<>();

  @Override
  public SystemUser getByTenant(String tenantId) {
    return TOKENS.get(tenantId);
  }

  @Override
  public boolean hasTokenForTenant(String tenantId) {
    return StringUtils.isNotBlank(TOKENS.getOrDefault(tenantId,
      new SystemUser()).getToken());
  }

  @Override
  public SystemUser save(String tenantId, SystemUser systemUser) {
    TOKENS.put(tenantId, new SystemUser(systemUser));
    return systemUser;
  }
}
