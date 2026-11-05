package org.folio.search.service.consortium;

import java.util.Collection;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.service.reindex.jdbc.MergeInstanceRepository;
import org.springframework.stereotype.Component;

/**
 * Resolves which instances are shared in a consortium.
 */
@Component
@RequiredArgsConstructor
public class SharedInstanceResolver {

  private final MergeInstanceRepository mergeInstanceRepository;
  private final ConsortiumTenantProvider consortiumTenantProvider;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;

  /**
   * Finds which of the given instances, referenced by records of the given tenant, are shared.
   * Instances referenced by the central tenant are always shared: its holdings can reference only its own instances,
   * and all of them are shared.
   *
   * @param tenantId    tenant of the records referencing the instances
   * @param instanceIds instance ids to check
   * @return ids of shared instances, empty for a non-consortium tenant
   */
  public Set<String> findSharedInstanceIds(String tenantId, Collection<String> instanceIds) {
    if (!consortiumTenantProvider.isConsortiumTenant(tenantId)) {
      return Set.of();
    }
    if (consortiumTenantProvider.isCentralTenant(tenantId)) {
      return Set.copyOf(instanceIds);
    }
    return consortiumTenantExecutor.execute(tenantId,
      () -> mergeInstanceRepository.findSharedInstanceIds(instanceIds));
  }
}
