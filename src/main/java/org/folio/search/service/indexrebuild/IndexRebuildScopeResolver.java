package org.folio.search.service.indexrebuild;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class IndexRebuildScopeResolver {

  private final ConsortiumTenantService consortiumTenantService;

  /**
   * Resolves tenants covered by a request.
   *
   * @param tenantId       requesting tenant
   * @param targetTenantId single tenant to rerun, null for the whole index
   * @throws RequestValidationException if the request came from a member tenant or the target tenant is not valid
   */
  public IndexRebuildScope resolve(String tenantId, String targetTenantId) {
    var central = consortiumTenantService.getCentralTenant(tenantId);
    if (central.isPresent() && !central.get().equals(tenantId)) {
      throw RequestValidationException.memberTenantNotAllowedException(tenantId);
    }

    var tenants = Stream.concat(Stream.of(tenantId), consortiumTenantService.getConsortiumTenants(tenantId).stream())
      .distinct()
      .toList();
    if (isBlank(targetTenantId)) {
      log.debug("resolve:: whole index [tenant: {}, tenants: {}]", tenantId, tenants);
      return new IndexRebuildScope(tenants, true);
    }
    if (central.isEmpty() || !tenants.contains(targetTenantId)) {
      throw RequestValidationException.targetTenantNotAllowedException(targetTenantId);
    }
    log.debug("resolve:: single tenant [tenant: {}, targetTenant: {}]", tenantId, targetTenantId);
    return new IndexRebuildScope(List.of(targetTenantId), false);
  }
}
