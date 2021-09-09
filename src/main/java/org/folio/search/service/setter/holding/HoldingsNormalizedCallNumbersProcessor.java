package org.folio.search.service.setter.holding;

import static java.util.stream.Collectors.toSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;
import static org.folio.search.utils.SearchUtils.getNormalizedCallNumber;

import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingsNormalizedCallNumbersProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toStreamSafe(instance.getHoldings())
      .filter(holding ->
        StringUtils.isNotEmpty(holding.getCallNumber()) || StringUtils.isNotEmpty(holding.getCallNumberPrefix()))
      .flatMap(holding -> Stream.concat(
        Stream.ofNullable(getNormalizedCallNumber(holding.getCallNumberPrefix(), holding.getCallNumber(),
          holding.getCallNumberSuffix())),
        Stream.ofNullable(getNormalizedCallNumber(holding.getCallNumber(), holding.getCallNumberSuffix()))))
      .collect(toSet());
  }
}
