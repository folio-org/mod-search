package org.folio.search.service.setter.item;

import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.model.client.CqlQueryParam.SOURCE;
import static org.folio.search.model.types.CallNumberTypeSource.LOCAL;
import static org.folio.search.service.browse.CallNumberBrowseService.FOLIO_CALL_NUMBER_TYPES_SOURCES;
import static org.folio.search.utils.CallNumberUtils.getCallNumberAsLong;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.CallNumberUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypedCallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  static final List<String> LOCAL_CALL_NUMBER_TYPES_SOURCES = Collections.singletonList(LOCAL.getSource());

  private final ReferenceDataService referenceDataService;

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
      .map(CallNumberType::getNumber)
      .or(() -> isLocalCallNumberTypeId(callNumberTypeId)
        ? Optional.of(CallNumberType.LOCAL.getNumber()) : Optional.empty());
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

  private boolean isLocalCallNumberTypeId(String callNumberTypeId) {
    return !referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, FOLIO_CALL_NUMBER_TYPES_SOURCES)
      .contains(callNumberTypeId);
  }

}
