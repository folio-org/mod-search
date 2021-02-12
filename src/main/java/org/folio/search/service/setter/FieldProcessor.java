package org.folio.search.service.setter;

import java.util.Map;

public interface FieldProcessor<T> {
  T getFieldValue(Map<String, Object> eventBody);
}
