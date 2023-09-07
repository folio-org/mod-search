package org.folio.search.service.consortium;

import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.UserTenantsClient;
import org.folio.spring.FolioExecutionContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumTenantService {

  private final UserTenantsClient userTenantsClient;

  private final FolioExecutionContext context;

  @Cacheable(cacheNames = USER_TENANTS_CACHE, key = "@folioExecutionContext.tenantId + ':' + #tenantId")
  public Optional<String> getCentralTenant(String tenantId) {
    var userTenants = userTenantsClient.getUserTenants(tenantId);
    log.debug("getCentralTenant: contextTenantId: {}, tenantId: {}, response: {}",
      context.getTenantId(), tenantId, userTenants);

    return Optional.ofNullable(userTenants)
      .flatMap(tenants -> tenants.userTenants().stream()
        .findFirst()
        .map(UserTenantsClient.UserTenant::centralTenantId));
  }

}
