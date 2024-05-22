package org.folio.search.converter;

import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.Item;
import org.springframework.stereotype.Component;

@Component
public class ConsortiumItemMapper {

  public ConsortiumItem map(String instanceId, Item item) {
    return new ConsortiumItem()
      .id(item.getId())
      .hrid(item.getHrid())
      .tenantId(item.getTenantId())
      .instanceId(instanceId)
      .holdingsRecordId(item.getHoldingsRecordId())
      .barcode(item.getBarcode());
  }
}
