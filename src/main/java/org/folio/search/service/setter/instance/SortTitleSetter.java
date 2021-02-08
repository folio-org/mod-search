package org.folio.search.service.setter.instance;

import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.service.setter.FieldSetter;
import org.springframework.stereotype.Component;

@Component
public final class SortTitleSetter implements FieldSetter<String> {
  @Override
  public String getFieldValue(Map<String, Object> eventBody) {
    return MapUtils.getString(eventBody, "title");
  }
}
