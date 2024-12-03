package org.folio.search.service.setter.callnumber;

import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.springframework.stereotype.Component;

@Component
public class NlmCallNumberShelvingOrderFieldProcessor extends CallNumberShelvingOrderFieldProcessor {
  protected NlmCallNumberShelvingOrderFieldProcessor() {
    super(number -> ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.NLM));
  }
}
