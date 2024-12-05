package org.folio.search.service.setter.callnumber;

import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.springframework.stereotype.Component;

@Component
public class DefaultCallNumberShelvingOrderFieldProcessor extends CallNumberShelvingOrderFieldProcessor {

  protected DefaultCallNumberShelvingOrderFieldProcessor() {
    super(number -> ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.DEFAULT));
  }
}
