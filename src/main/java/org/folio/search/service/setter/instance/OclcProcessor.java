package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.trim;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;

/**
 * Identifier field processor, which normalize OCLC numbers.
 */
@Component
public class OclcProcessor extends AbstractIdentifierProcessor<Instance> {

  private static final List<String> OCLC_IDENTIFIER_NAMES = List.of("OCLC", "Cancelled OCLC");

  private static final Pattern OCLC_REGEX = Pattern.compile("([1-9][0-9\\-]*)");
  private static final Pattern WHITESPACE_REGEX = Pattern.compile("\\s+");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link org.folio.search.integration.ReferenceDataService} bean
   */
  public OclcProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, OCLC_IDENTIFIER_NAMES);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return filterIdentifiersValue(instance.getIdentifiers()).stream()
      .map(this::normalizeOclc)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
  }

  /**
   * Returns normalized isbn value.
   *
   * @param value value to process as {@link String}
   * @return normalized isbn value
   */
  public String normalizeOclc(String value) {
    var oclcValue = normalizeOclcValue(value);
    if (StringUtils.isBlank(oclcValue)) {
      return null;
    }

    var sb = new StringBuilder();
    var matcher = OCLC_REGEX.matcher(oclcValue);
    if (matcher.find()) {
      sb.append(matcher.group(0));
    }

    if (value.charAt(value.length() - 1) == '*') {
      sb.append('*');
    }

    return sb.toString();
  }

  private String normalizeOclcValue(String value) {
    return WHITESPACE_REGEX.matcher(trim(value)).replaceAll("-");
  }

}
