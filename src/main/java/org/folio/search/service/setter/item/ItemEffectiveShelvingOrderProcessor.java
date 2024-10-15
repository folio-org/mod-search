package org.folio.search.service.setter.item;

import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.CallNumberUtils;
import org.springframework.stereotype.Component;

@Component
public class ItemEffectiveShelvingOrderProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance eventBody) {
    return toStreamSafe(eventBody.getItems())
      .map(CallNumberUtils::calculateShelvingOrder)
      .map(CallNumberUtils::normalizeEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .sorted()
      .collect(toLinkedHashSet());
  }

}
