package org.folio.search.service.setter.holding;

import static java.util.stream.Collectors.toSet;
import static org.codehaus.plexus.util.StringUtils.isNotBlank;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingsTypeIdProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getHoldings())
      .filter(holding -> isNotBlank(holding.getTypeId()))
      .map(holding -> holding.getTypeId().trim())
      .collect(toSet());
  }
}
