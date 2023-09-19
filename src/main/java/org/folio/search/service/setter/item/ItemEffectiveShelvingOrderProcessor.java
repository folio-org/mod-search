package org.folio.search.service.setter.item;

import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemEffectiveShelvingOrderProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance eventBody) {
    return toStreamSafe(eventBody.getItems())
      .map(this::getNormalizedEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .sorted()
      .collect(toLinkedHashSet());
  }

  private String getNormalizedEffectiveShelvingOrder(Item item) {
    var effectiveShelvingOrder = item.getEffectiveShelvingOrder();
    return Optional.ofNullable(item.getEffectiveCallNumberComponents())
      .flatMap(components -> CallNumberType.fromId(components.getTypeId()))
      .filter(callNumberType -> callNumberType.getNumber() < 5)
      .map(callNumberType -> effectiveShelvingOrder)
      .orElse(normalizeEffectiveShelvingOrder(effectiveShelvingOrder));
  }

}
