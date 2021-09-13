package org.folio.search.service.setter.holding;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;
import static org.folio.search.utils.SearchUtils.getNormalizedCallNumber;

import java.util.HashSet;
import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class HoldingsNormalizedCallNumbersProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var result = new HashSet<String>();
    toStreamSafe(instance.getHoldings())
      .filter(holding -> isNotEmpty(holding.getCallNumber()) || isNotEmpty(holding.getCallNumberPrefix()))
      .forEach(holding -> {
        result.add(getNormalizedCallNumber(holding.getCallNumberPrefix(), holding.getCallNumber(),
          holding.getCallNumberSuffix()));
        result.add(getNormalizedCallNumber(holding.getCallNumber(), holding.getCallNumberSuffix()));
      });
    return result;
  }
}
