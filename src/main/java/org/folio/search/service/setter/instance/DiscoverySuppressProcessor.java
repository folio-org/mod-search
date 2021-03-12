package org.folio.search.service.setter.instance;

import java.util.Map;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class DiscoverySuppressProcessor implements FieldProcessor<Boolean> {

  @Override
  public Boolean getFieldValue(Map<String, Object> eventBody) {
    return MapUtils.getBoolean(eventBody, "discoverySuppress", false);
  }
}
