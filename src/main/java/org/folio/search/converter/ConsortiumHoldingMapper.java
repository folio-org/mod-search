package org.folio.search.converter;

import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.Holding;
import org.springframework.stereotype.Component;

@Component
public class ConsortiumHoldingMapper {

  public ConsortiumHolding map(String instanceId, Holding holding) {
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
