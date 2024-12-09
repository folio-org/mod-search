package org.folio.search.service.setter.callnumber;

import java.util.function.UnaryOperator;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.service.setter.common.shelving.ShelvingOrderFieldProcessor;

public abstract class CallNumberShelvingOrderFieldProcessor
  extends ShelvingOrderFieldProcessor<CallNumberResource> {

  protected CallNumberShelvingOrderFieldProcessor(UnaryOperator<String> numberFunction) {
    super(numberFunction);
  }

  @Override
  protected String extractNumber(CallNumberResource callNumberResource) {
    return callNumberResource.fullCallNumber();
  }
}
