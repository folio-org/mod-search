package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class IssnProcessor extends AbstractIdentifierProcessor {

  private static final String ISSN_IDENTIFIER_TYPE_ID = "913300b2-03ed-469a-8179-c1092c991227";

  public IssnProcessor(JsonConverter jsonConverter) {
    super(jsonConverter);
  }

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    return getInstanceIdentifiers(eventBody).stream()
      .filter(identifier -> ISSN_IDENTIFIER_TYPE_ID.equals(identifier.getIdentifierTypeId()))
      .map(InstanceIdentifiers::getValue)
      .filter(Objects::nonNull)
      .map(String::trim)
      .collect(toList());
  }
}
