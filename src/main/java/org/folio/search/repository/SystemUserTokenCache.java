package org.folio.search.repository;

public interface SystemUserTokenCache {

  String getTokenByTenant(String tenantId);

  boolean hasTokenForTenant(String tenantId);

  void save(String tenantId, String token);
}
