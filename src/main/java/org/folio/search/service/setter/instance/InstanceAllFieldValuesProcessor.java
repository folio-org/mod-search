package org.folio.search.service.setter.instance;

import static org.folio.search.utils.CollectionUtils.noneMatch;

import java.util.Map;
import java.util.Set;
import org.folio.search.model.service.MultilangValue;
import org.folio.search.service.setter.AbstractAllValuesProcessor;
import org.springframework.stereotype.Component;

@Component
public class InstanceAllFieldValuesProcessor extends AbstractAllValuesProcessor {

  private static final Set<String> NESTED_RESOURCE_PREFIXES = Set.of("item", "holding");

  @Override
  public MultilangValue getFieldValue(Map<String, Object> event) {
    return getAllFieldValues(event, null, key -> noneMatch(NESTED_RESOURCE_PREFIXES, key::startsWith));
  }
}
