package org.folio.search.service.setter.item;

import static java.util.stream.Collectors.toSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;
import static org.folio.search.utils.SearchUtils.getNormalizedCallNumber;

import java.util.Set;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemsNormalizedCallNumbersProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .filter(items -> items.getEffectiveCallNumberComponents() != null)
      .flatMap(item -> {
        var itemCallNumber = item.getEffectiveCallNumberComponents();
        return Stream.concat(
          Stream.ofNullable(getNormalizedCallNumber(itemCallNumber.getPrefix(),
            itemCallNumber.getCallNumber(),
            itemCallNumber.getSuffix())),
          Stream.ofNullable(getNormalizedCallNumber(itemCallNumber.getCallNumber(),
            itemCallNumber.getSuffix())));
      })
      .collect(toSet());
  }
}
