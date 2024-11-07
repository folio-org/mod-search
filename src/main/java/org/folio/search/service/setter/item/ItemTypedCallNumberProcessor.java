package org.folio.search.service.setter.item;

import static org.folio.search.utils.CallNumberUtils.getCallNumberAsLong;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.CallNumberUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypedCallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  @Override
  public Set<Long> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .map(this::toCallNumberLongRepresentation)
      .filter(Objects::nonNull)
      .filter(value -> value > 0)
      .sorted()
      .collect(toLinkedHashSet());
  }

  public Optional<Integer> getCallNumberTypedPrefix(String callNumberTypeId) {
    return CallNumberType.fromId(callNumberTypeId)
      .map(CallNumberType::getNumber);
  }

  private Long toCallNumberLongRepresentation(Item item) {
    var effectiveShelvingOrder = CallNumberUtils.calculateShelvingOrder(item);
    var callNumberTypeId = Optional.ofNullable(item.getEffectiveCallNumberComponents())
      .map(ItemEffectiveCallNumberComponents::getTypeId)
      .orElse(null);
    if (StringUtils.isAnyBlank(callNumberTypeId, effectiveShelvingOrder)) {
      return null;
    } else {
      return getCallNumberTypedPrefix(callNumberTypeId)
        .map(integer -> getCallNumberAsLong(effectiveShelvingOrder, integer))
        .orElse(null);
    }
  }

}
