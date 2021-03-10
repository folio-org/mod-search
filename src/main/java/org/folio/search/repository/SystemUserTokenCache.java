package org.folio.search.repository;

import org.folio.search.model.SystemUser;

public interface SystemUserTokenCache {

  SystemUser getByTenant(String tenantId);

  boolean hasTokenForTenant(String tenantId);

  SystemUser save(String tenantId, SystemUser systemUser);
}
