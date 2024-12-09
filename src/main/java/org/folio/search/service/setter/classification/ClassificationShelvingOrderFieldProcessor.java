package org.folio.search.service.setter.classification;

import java.util.function.UnaryOperator;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.service.setter.common.shelving.ShelvingOrderFieldProcessor;

public abstract class ClassificationShelvingOrderFieldProcessor
  extends ShelvingOrderFieldProcessor<ClassificationResource> {

  protected ClassificationShelvingOrderFieldProcessor(UnaryOperator<String> numberFunction) {
    super(numberFunction);
  }

  @Override
  protected String extractNumber(ClassificationResource classificationResource) {
    return classificationResource.number();
  }
}
