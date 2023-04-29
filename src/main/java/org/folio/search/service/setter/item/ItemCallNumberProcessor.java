package org.folio.search.service.setter.item;

import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.CallNumberUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ItemCallNumberProcessor implements FieldProcessor<Instance, Set<Long>> {

  @Override
  public Set<Long> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .map(Item::getEffectiveShelvingOrder)
      .filter(StringUtils::isNotBlank)
      .map(CallNumberUtils::getCallNumberAsLong)
      .filter(value -> value > 0)
      .sorted()
      .collect(toLinkedHashSet());
  }
}
