package org.folio.search.service.setter.common.shelving;

import java.util.function.UnaryOperator;
import lombok.NonNull;
import org.folio.search.service.setter.FieldProcessor;

public abstract class ShelvingOrderFieldProcessor<T>
  implements FieldProcessor<T, String> {

  private final UnaryOperator<String> numberFunction;

  protected ShelvingOrderFieldProcessor(@NonNull UnaryOperator<String> numberFunction) {
    this.numberFunction = numberFunction;
  }

  @Override
  public String getFieldValue(T eventBody) {
    var number = extractNumber(eventBody);
    return numberFunction.apply(number);
  }

  protected abstract String extractNumber(T eventBody);
}

