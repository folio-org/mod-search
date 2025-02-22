package org.folio.search.service.setter.authority;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HeadingRefProcessor extends AbstractAuthorityProcessor {

  @Override
  public String getFieldValue(Map<String, Object> eventBody) {
    return eventBody.entrySet().stream()
      .map(entry -> getAuthorityFieldForEntry(entry)
        .map(fieldDesc -> getStringValue(entry.getValue())))
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(null);
  }

  private static String getStringValue(Object value) {
    if (value instanceof String string) {
      return string;
    }
    if (value instanceof Iterable<?> iterable) {
      return getStringValue(iterable.iterator().next());
    }
    return null;
  }
}
