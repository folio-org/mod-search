package org.folio.search.service.setter.instance;

import static org.apache.commons.collections4.MapUtils.getObject;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.service.indexrebuild.callnumber.CallNumberKey;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class CallNumberIdsProcessor implements FieldProcessor<Map<String, Object>, Set<String>> {

  @Override
  public Set<String> getFieldValue(Map<String, Object> instance) {
    @SuppressWarnings("unchecked")
    var items = (List<Map<String, Object>>) getObject(instance, INSTANCE_ITEM_FIELD_NAME);
    if (items == null || items.isEmpty()) {
      return Set.of();
    }
    return items.stream()
      .map(CallNumberKey::fromItem)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .map(CallNumberKey::id)
      .collect(Collectors.toSet());
  }
}
