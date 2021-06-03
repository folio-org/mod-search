package org.folio.search.service.setter.item;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.folio.search.utils.SearchUtils.getEffectiveCallNumber;

import java.util.Objects;
import java.util.Set;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class EffectiveCallNumberComponentsProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var items = instance.getItems();
    if (CollectionUtils.isEmpty(items)) {
      return emptySet();
    }

    return items.stream()
      .map(Item::getEffectiveCallNumberComponents)
      .filter(Objects::nonNull)
      .map(cn -> getEffectiveCallNumber(cn.getPrefix(), cn.getCallNumber(), cn.getSuffix()))
      .filter(StringUtils::isNotBlank)
      .collect(toSet());
  }
}
