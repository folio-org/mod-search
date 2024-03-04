package org.folio.search.service.setter.classification;

import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.utils.ShelvingOrderCalculationHelper;
import org.springframework.stereotype.Component;

@Component
public class LcClassificationShelvingOrderFieldProcessor extends ClassificationShelvingOrderFieldProcessor {

  protected LcClassificationShelvingOrderFieldProcessor() {
    super(number -> ShelvingOrderCalculationHelper.calculate(number, ShelvingOrderAlgorithmType.LC));
  }
}
