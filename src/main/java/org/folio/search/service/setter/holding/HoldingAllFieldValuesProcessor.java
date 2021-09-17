package org.folio.search.service.setter.holding;

import static org.apache.commons.collections.MapUtils.getObject;

import java.util.Map;
import org.folio.search.model.service.MultilangValue;
import org.folio.search.service.setter.AbstractAllValuesProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingAllFieldValuesProcessor extends AbstractAllValuesProcessor {

  @Override
  public MultilangValue getFieldValue(Map<String, Object> eventBody) {
    var holdings = getObject(eventBody, "holdings");
    var resultMultilangValue = getAllFieldValues(holdings, "holdings", key -> true);
    var searchMultilangValue = getAllFieldValues(eventBody, null, key -> key.startsWith("holding"));
    resultMultilangValue.getPlainValues().addAll(searchMultilangValue.getPlainValues());
    resultMultilangValue.getMultilangValues().addAll(searchMultilangValue.getMultilangValues());
    return resultMultilangValue;
  }
}
