package org.folio.search.converter;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.Item;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConsortiumItemMapper {

  public static ConsortiumItem toConsortiumItem(String instanceId, Item item) {
    return new ConsortiumItem()
      .id(item.getId())
      .hrid(item.getHrid())
      .tenantId(item.getTenantId())
      .instanceId(instanceId)
      .holdingsRecordId(item.getHoldingsRecordId())
      .barcode(item.getBarcode());
  }
}
