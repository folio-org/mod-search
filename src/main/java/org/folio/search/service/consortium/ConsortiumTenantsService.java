package org.folio.search.service.consortium;

import static org.folio.search.configuration.SearchCacheNames.CONSORTIUM_TENANTS_CACHE;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.client.ConsortiumTenantsClient;
import org.folio.search.exception.FolioIntegrationException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumTenantsService {

  private static final int DEFAULT_REQUEST_LIMIT = 10000;

  private final UserTenantsService userTenantsService;
  private final ConsortiumTenantsClient consortiumTenantsClient;

  /**
   * Get consortium tenants for tenantId.
   *
   * @return only consortium member tenants
  * */
  @Cacheable(cacheNames = CONSORTIUM_TENANTS_CACHE, key = "@folioExecutionContext.tenantId + ':' + #tenantId")
  public List<String> getConsortiumTenants(String tenantId) {
    try {
      return userTenantsService.getConsortiumId(tenantId)
        .map(consortiumId -> consortiumTenantsClient.getConsortiumTenants(consortiumId, DEFAULT_REQUEST_LIMIT))
        .map(ConsortiumTenantsClient.ConsortiumTenants::tenants)
        .map(this::getTenantsList)
        .orElse(Collections.emptyList());
    } catch (Exception e) {
      log.debug("Unexpected exception occurred while trying to get consortium tenants", e);
      throw new FolioIntegrationException("Failed to retrieve consortium tenants", e);
    }
  }

  private List<String> getTenantsList(List<ConsortiumTenantsClient.ConsortiumTenant> consortiumTenants) {
    return consortiumTenants.stream()
      .filter(consortiumTenant -> !consortiumTenant.isCentral())
      .map(ConsortiumTenantsClient.ConsortiumTenant::id)
      .toList();
  }
}
