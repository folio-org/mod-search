package org.folio.search.repository;

import java.util.Optional;
import org.folio.search.model.SystemUser;

public interface SystemUserRepository {
  Optional<SystemUser> getByTenantId(String tenantId);

  void save(SystemUser systemUser);
}
