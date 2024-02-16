package org.folio.search.service.setter.instance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;
import org.folio.search.utils.SearchUtils;
import org.springframework.stereotype.Component;

/**
 * Instance identifier field processor, which normalizes LCCN numbers.
 */
@Component
public class LccnInstanceProcessor extends AbstractIdentifierProcessor<Instance> {

  private static final List<String> LCCN_IDENTIFIER_NAME = List.of("LCCN", "Canceled LCCN");

  private static final Pattern LCCN_NUMERIC_PART_REGEX = Pattern.compile("([1-9]\\d+)");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public LccnInstanceProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return filterIdentifiersValue(instance.getIdentifiers()).stream()
      .flatMap(value -> Stream.of(SearchUtils.normalizeLccn(value), extractNumericPart(value)))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private String extractNumericPart(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }

    var matcher = LCCN_NUMERIC_PART_REGEX.matcher(value);
    return matcher.find() ? matcher.group(0) : null;
  }
}
