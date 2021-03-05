package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class IssnProcessor extends AbstractIdentifierProcessor {

  static final String ISSN_IDENTIFIER_TYPE_ID = "913300b2-03ed-469a-8179-c1092c991227";
  static final String INVALID_ISSN_IDENTIFIER_TYPE_ID = "27fd35a6-b8f6-41f2-aa0e-9c663ceb250c";

  private final Set<String> issnIdentifierTypeIds = Set.of(ISSN_IDENTIFIER_TYPE_ID, INVALID_ISSN_IDENTIFIER_TYPE_ID);

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public IssnProcessor(JsonConverter jsonConverter) {
    super(jsonConverter);
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
  protected Set<String> getIdentifierTypeIds() {
    return issnIdentifierTypeIds;
  }
}
