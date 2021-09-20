package org.folio.search.service.setter.item;

import static org.apache.commons.collections.MapUtils.getObject;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;

import java.util.Map;
import org.folio.search.model.service.MultilangValue;
import org.folio.search.service.setter.AbstractAllValuesProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemAllFieldValuesProcessor extends AbstractAllValuesProcessor {

  @Override
  public MultilangValue getFieldValue(Map<String, Object> eventBody) {
    var items = getObject(eventBody, INSTANCE_ITEM_FIELD_NAME);
    var resultMultilangValue = getAllFieldValues(items, INSTANCE_ITEM_FIELD_NAME, key -> true);
    var searchFieldMultilangValue = getAllFieldValues(eventBody, null, key -> key.startsWith("item"));
    resultMultilangValue.getPlainValues().addAll(searchFieldMultilangValue.getPlainValues());
    resultMultilangValue.getMultilangValues().addAll(searchFieldMultilangValue.getMultilangValues());
    return resultMultilangValue;
  }
}
