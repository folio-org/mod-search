package org.folio.search.repository.impl;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.model.SystemUser;
import org.folio.search.repository.SystemUserRepository;
import org.springframework.stereotype.Component;

@Log4j2
@AllArgsConstructor
@Component
public class InMemorySystemUserRepository implements SystemUserRepository {
  private static final Map<String, SystemUser> SYSTEM_USERS = new ConcurrentHashMap<>();

  @Override
  public Optional<SystemUser> getByTenantId(String tenantId) {
    return Optional.ofNullable(SYSTEM_USERS.get(tenantId));
  }

  @Override
  public void save(SystemUser systemUser) {
    SYSTEM_USERS.put(systemUser.getTenantId(), systemUser);
  }
}
