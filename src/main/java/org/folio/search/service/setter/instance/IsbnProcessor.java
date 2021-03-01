package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;
import static org.folio.isbn.IsbnUtil.convertTo13DigitNumber;
import static org.folio.isbn.IsbnUtil.isValid10DigitNumber;
import static org.folio.search.utils.SearchUtils.removeHyphens;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
public class IsbnProcessor extends AbstractIdentifierProcessor {

  private static final String ISBN_IDENTIFIER_TYPE_ID = "8261054f-be78-422d-bd51-4ed9f33c3422";
  private static final String INVALID_ISBN_IDENTIFIER_TYPE_ID = "fcca2643-406a-482a-b760-7a7f8aec640e";

  /**
   * Used by dependency injection.
   *
   * @param jsonConverter {@link JsonConverter} bean
   */
  public IsbnProcessor(JsonConverter jsonConverter) {
    super(jsonConverter);
  }

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    return getInstanceIdentifiers(eventBody).stream()
      .filter(identifier -> ISBN_IDENTIFIER_TYPE_ID.equals(identifier.getIdentifierTypeId()))
      .map(InstanceIdentifiers::getValue)
      .filter(Objects::nonNull)
      .map(String::trim)
      .flatMap(IsbnProcessor::normalizeIsbn)
      .collect(toList());
  }

  private static Stream<String> normalizeIsbn(String value) {
    if (StringUtils.isBlank(value)) {
      return Stream.empty();
    }
    String cleanValue = removeHyphens(value);
    if (isValid10DigitNumber(cleanValue)) {
      return Stream.of(cleanValue, removeHyphens(convertTo13DigitNumber(cleanValue)));
    }
    return Stream.of(cleanValue);
  }
}
