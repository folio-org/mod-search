package org.folio.search.service.consortium;

import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.search.client.UserTenantsClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConsortiumTenantService {

  private final UserTenantsClient userTenantsClient;

  @Cacheable(USER_TENANTS_CACHE)
  public Optional<String> getCentralTenant(String tenantId) {
    var userTenants = userTenantsClient.getUserTenants(tenantId);

    return Optional.ofNullable(userTenants)
      .flatMap(tenants -> tenants.userTenants().stream()
        .findFirst()
        .map(UserTenantsClient.UserTenant::centralTenantId));
  }

}
