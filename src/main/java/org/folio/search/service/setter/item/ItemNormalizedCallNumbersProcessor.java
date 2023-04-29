package org.folio.search.service.setter.item;

import static org.folio.search.utils.CallNumberUtils.normalizeCallNumberComponents;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.HashSet;
import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemNormalizedCallNumbersProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var result = new HashSet<String>();
    toStreamSafe(instance.getItems())
      .filter(items -> items.getEffectiveCallNumberComponents() != null)
      .forEach(item -> {
        var itemCallNumber = item.getEffectiveCallNumberComponents();
        result.add(normalizeCallNumberComponents(itemCallNumber.getPrefix(), itemCallNumber.getCallNumber(),
          itemCallNumber.getSuffix()));
        result.add(normalizeCallNumberComponents(itemCallNumber.getCallNumber(), itemCallNumber.getSuffix()));
      });
    return result;
  }
}
