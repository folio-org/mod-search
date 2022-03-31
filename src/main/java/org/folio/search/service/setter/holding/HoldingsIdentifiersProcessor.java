package org.folio.search.service.setter.holding;

import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingsIdentifiersProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getHoldings())
      .flatMap(holding -> StreamEx.of(toStreamSafe(holding.getFormerIds()))
        .append(Stream.of(holding.getHrid()))
        .append(Stream.of(holding.getId())))
      .filter(StringUtils::isNotEmpty)
      .collect(Collectors.toSet());
  }
}
