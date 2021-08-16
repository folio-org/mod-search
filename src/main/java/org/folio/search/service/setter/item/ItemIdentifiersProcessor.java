package org.folio.search.service.setter.item;

import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class ItemIdentifiersProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getItems())
      .flatMap(item -> Stream.concat(Stream.concat(toStreamSafe(item.getFormerIds()), Stream.of(item.getHrid())),
        Stream.of(item.getAccessionNumber())))
      .filter(StringUtils::isNotEmpty)
      .collect(Collectors.toSet());
  }
}
