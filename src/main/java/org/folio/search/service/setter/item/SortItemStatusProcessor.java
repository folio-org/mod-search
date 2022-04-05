package org.folio.search.service.setter.item;

import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public final class SortItemStatusProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return instance.getItems().stream()
      .map(item -> item.getStatus().getName())
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toSet());
  }
}
