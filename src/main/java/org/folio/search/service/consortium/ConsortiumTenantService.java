package org.folio.search.service.consortium;

import static org.folio.search.configuration.SearchCacheNames.CONSORTIUM_TENANTS_CACHE;
import static org.folio.search.configuration.SearchCacheNames.USER_TENANTS_CACHE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.client.ConsortiumTenantsClient;
import org.folio.search.client.UserTenantsClient;
import org.folio.search.exception.FolioIntegrationException;
import org.folio.spring.FolioExecutionContext;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumTenantService {

  private static final int DEFAULT_REQUEST_LIMIT = 10000;

  private final UserTenantsClient userTenantsClient;
  private final ConsortiumTenantsClient consortiumTenantsClient;
  private final FolioExecutionContext context;

  @Cacheable(cacheNames = USER_TENANTS_CACHE, key = "@folioExecutionContext.tenantId + ':' + #tenantId")
  public Optional<String> getCentralTenant(String tenantId) {
    if (StringUtils.isBlank(tenantId)) {
      return Optional.empty();
    }

    var userTenants = userTenantsClient.getUserTenants(tenantId);
    log.debug("getCentralTenant: contextTenantId: {}, tenantId: {}, response: {}",
      context.getTenantId(), tenantId, userTenants);

    return Optional.ofNullable(userTenants)
      .flatMap(tenants -> tenants.userTenants().stream()
        .findFirst()
        .map(UserTenantsClient.UserTenant::centralTenantId));
  }

  /**
   * Get consortium id.
   *
   * @return consortium id if passed 'tenantId' is a part of a consortium
   * */
  public Optional<String> getConsortiumId(String tenantId) {
    if (StringUtils.isBlank(tenantId)) {
      return Optional.empty();
    }

    var userTenantsResponse = userTenantsClient.getUserTenants(tenantId);
    if (userTenantsResponse != null) {
      return userTenantsResponse.userTenants().stream()
        .filter(userTenant -> userTenant.centralTenantId().equals(tenantId))
        .findFirst()
        .map(UserTenantsClient.UserTenant::consortiumId);
    }
    return Optional.empty();
  }

  /**
   * Get consortium tenants for tenantId.
   *
   * @return only consortium member tenants
  * */
  @Cacheable(cacheNames = CONSORTIUM_TENANTS_CACHE, key = "@folioExecutionContext.tenantId + ':' + #tenantId")
  public List<String> getConsortiumTenants(String tenantId) {
    try {
      return getConsortiumId(tenantId)
        .map(consortiumId -> consortiumTenantsClient.getConsortiumTenants(consortiumId, DEFAULT_REQUEST_LIMIT))
        .map(ConsortiumTenantsClient.ConsortiumTenants::tenants)
        .map(this::getTenantsList)
        .orElse(Collections.emptyList());
    } catch (Exception e) {
      log.debug("Unexpected exception occurred while trying to get consortium tenants", e);
      throw new FolioIntegrationException("Failed to retrieve consortium tenants", e);
    }
  }

  public boolean isMemberTenantInConsortium(String tenantId) {
    return getCentralTenant(tenantId)
      .map(centralTenantId -> !centralTenantId.equals(tenantId))
      .orElse(false);
  }

  public boolean isCentralTenant(String tenantId) {
    return getCentralTenant(tenantId)
      .map(centralTenantId -> centralTenantId.equals(tenantId))
      .orElse(false);
  }

  private List<String> getTenantsList(List<ConsortiumTenantsClient.ConsortiumTenant> consortiumTenants) {
    return consortiumTenants.stream()
      .filter(consortiumTenant -> !consortiumTenant.isCentral())
      .map(ConsortiumTenantsClient.ConsortiumTenant::id)
      .toList();
  }
}
