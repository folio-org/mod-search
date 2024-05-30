package org.folio.search.converter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.Holding;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConsortiumHoldingMapper {

  public static ConsortiumHolding toConsortiumHolding(String instanceId, Holding holding) {
    return new ConsortiumHolding()
      .id(holding.getId())
      .hrid(holding.getHrid())
      .tenantId(holding.getTenantId())
      .instanceId(instanceId)
      .callNumberPrefix(holding.getCallNumberPrefix())
      .callNumber(holding.getCallNumber())
      .callNumberSuffix(holding.getCallNumberSuffix())
      .copyNumber(holding.getCopyNumber())
      .permanentLocationId(holding.getPermanentLocationId())
      .discoverySuppress(holding.getDiscoverySuppress() != null && holding.getDiscoverySuppress());
  }
}
