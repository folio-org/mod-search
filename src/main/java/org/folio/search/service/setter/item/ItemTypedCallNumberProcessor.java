package org.folio.search.service.setter.item;

import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.CALL_NUMBER_TYPES;
import static org.folio.search.model.client.CqlQueryParam.SOURCE;
import static org.folio.search.utils.CallNumberUtils.getCallNumberAsLong;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemTypedCallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  static final String LC_ID = "95467209-6d7b-468b-94df-0f5d7ad2747d";
  static final String DEWEY_ID = "03dd64d0-5626-4ecd-8ece-4531e0069f35";
  static final String NLM_ID = "054d460d-d6b9-4469-9e37-7a78a2266655";
  static final String SUDOC_ID = "fc388041-6cd0-4806-8a74-ebe3b9ab4c6e";
  static final String OTHER_SCHEME_ID = "6caca63e-5651-4db6-9247-3205156e9699";

  private static final String SYSTEM_SOURCE = "system";
  private static final String LOCAL_SOURCE = "local";
  static final List<String> LOCAL_CALL_NUMBER_TYPES_SOURCES = Collections.singletonList(LOCAL_SOURCE);
  private static final Map<CallNumberKey, Integer> CALL_NUMBER_PREFIX_MAP = Map.of(
    new CallNumberKey(StringUtils.EMPTY, LOCAL_SOURCE), 0,
    new CallNumberKey(LC_ID, SYSTEM_SOURCE), 1,
    new CallNumberKey(DEWEY_ID, SYSTEM_SOURCE), 2,
    new CallNumberKey(NLM_ID, SYSTEM_SOURCE), 3,
    new CallNumberKey(SUDOC_ID, SYSTEM_SOURCE), 4,
    new CallNumberKey(OTHER_SCHEME_ID, SYSTEM_SOURCE), 5
  );

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

  private Long toCallNumberLongRepresentation(Item item) {
    var effectiveShelvingOrder = item.getEffectiveShelvingOrder();
    var callNumberTypeId = item.getItemLevelCallNumberTypeId();
    if (StringUtils.isAnyBlank(callNumberTypeId, effectiveShelvingOrder)) {
      return null;
    } else {
      return getCallNumberTypedPrefix(callNumberTypeId)
        .map(integer -> getCallNumberAsLong(effectiveShelvingOrder, integer))
        .orElse(null);
    }
  }

  public Optional<Integer> getCallNumberTypedPrefix(String callNumberTypeId) {
    boolean isLocal = isLocalCallNumberTypeId(callNumberTypeId);
    var callNumberKey = new CallNumberKey(callNumberTypeId, isLocal ? LOCAL_SOURCE : SYSTEM_SOURCE);
    return Optional.ofNullable(CALL_NUMBER_PREFIX_MAP.get(callNumberKey));
  }

  private boolean isLocalCallNumberTypeId(String callNumberTypeId) {
    return referenceDataService.fetchReferenceData(CALL_NUMBER_TYPES, SOURCE, LOCAL_CALL_NUMBER_TYPES_SOURCES)
      .contains(callNumberTypeId);
  }

  public record CallNumberKey(String callNumberTypeId, String callNumberSource) {

    public CallNumberKey(String callNumberTypeId, String callNumberSource) {
      this.callNumberSource = callNumberSource;
      this.callNumberTypeId = !callNumberSource.equals(SYSTEM_SOURCE) ? StringUtils.EMPTY : callNumberTypeId;
    }
  }
}
