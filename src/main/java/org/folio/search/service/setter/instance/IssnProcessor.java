package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class IssnProcessor extends AbstractIdentifierProcessor {
  private static final List<String> IDENTIFIER_NAMES = List.of("ISSN", "Invalid ISSN");

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public IssnProcessor(JsonConverter jsonConverter, InstanceIdentifierTypeCache cache) {
    super(jsonConverter, cache);
  }

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    return getInstanceIdentifiers(eventBody).stream()
      .map(InstanceIdentifiers::getValue)
      .filter(Objects::nonNull)
      .map(String::trim)
      .collect(toList());
  }

  @Override
  protected List<String> getIdentifierNames() {
    return IDENTIFIER_NAMES;
  }
}
