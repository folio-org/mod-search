package org.folio.search.service.indexrebuild.item;

import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.Strings;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.springframework.stereotype.Component;

/**
 * Resolves whether the instance of an item is shared in a consortium, from the {@code instanceSource} provided by
 * inventory. Instances of the central tenant are always shared, and don't have the consortium source prefix.
 */
@Component
@RequiredArgsConstructor
public class ItemSharedFlagResolver {

  private static final String INSTANCE_SOURCE_FIELD = "instanceSource";

  private final ConsortiumTenantProvider consortiumTenantProvider;

  public boolean isShared(String tenantId, Map<?, ?> item) {
    return item.get(INSTANCE_SOURCE_FIELD) instanceof String source
           && Strings.CS.startsWith(source, SOURCE_CONSORTIUM_PREFIX)
           || consortiumTenantProvider.isCentralTenant(tenantId);
  }
}
