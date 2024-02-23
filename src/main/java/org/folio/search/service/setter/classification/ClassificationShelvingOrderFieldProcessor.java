package org.folio.search.service.setter.classification;

import java.util.function.Function;
import lombok.NonNull;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.service.setter.FieldProcessor;

public abstract class ClassificationShelvingOrderFieldProcessor
  implements FieldProcessor<ClassificationResource, String> {

  private final Function<String, String> numberFunction;

  protected ClassificationShelvingOrderFieldProcessor(@NonNull Function<String, String> numberFunction) {
    this.numberFunction = numberFunction;
  }

  @Override
  public String getFieldValue(ClassificationResource eventBody) {
    var number = eventBody.number();
    return numberFunction.apply(number);
  }
}

