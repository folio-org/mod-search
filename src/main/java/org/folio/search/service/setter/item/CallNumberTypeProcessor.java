package org.folio.search.service.setter.item;

import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.model.client.CqlQueryParam.SOURCE;
import static org.folio.search.service.browse.CallNumberBrowseService.FOLIO_CALL_NUMBER_TYPES_SOURCES;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.model.types.CallNumberType;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallNumberTypeProcessor implements FieldProcessor<Instance, Set<String>> {

  private final ReferenceDataService referenceDataService;

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .map(this::itemToCallNumberTypeString)
      .filter(Objects::nonNull)
      .sorted()
      .collect(toLinkedHashSet());
  }

  private String itemToCallNumberTypeString(Item item) {
    return Optional.ofNullable(item.getEffectiveCallNumberComponents())
      .map(ItemEffectiveCallNumberComponents::getTypeId)
      .map(this::callNumberTypeIdToString)
      .orElse(null);
  }

  private String callNumberTypeIdToString(String callNumberTypeId) {
    return CallNumberType.fromId(callNumberTypeId)
      .map(CallNumberType::toString)
      .or(() -> isLocalCallNumberTypeId(callNumberTypeId)
        ? Optional.of(CallNumberType.LOCAL.toString()) : Optional.empty())
      .orElse(null);
  }

  private boolean isLocalCallNumberTypeId(String callNumberTypeId) {
    return !referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, FOLIO_CALL_NUMBER_TYPES_SOURCES)
      .contains(callNumberTypeId);
  }

}
