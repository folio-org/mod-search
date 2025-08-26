package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.lang3.StringUtils.trim;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.springframework.stereotype.Component;

/**
 * Identifier field processor, which normalize OCLC numbers.
 */
@Component
public class OclcProcessor extends AbstractInstanceIdentifierProcessor {

  private static final List<String> OCLC_IDENTIFIER_NAMES = List.of("OCLC", "Cancelled OCLC");

  private static final Pattern OCLC_REGEX = Pattern.compile("([1-9][\\d\\-]*)");
  private static final Pattern WHITESPACE_REGEX = Pattern.compile("\\s+");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public OclcProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return getIdentifierValuesStream(instance)
      .map(this::normalizeOclc)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
  }

  /**
   * Returns normalized oclc value.
   *
   * @param value value to process as {@link String}
   * @return normalized oclc value
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

  @Override
  public List<String> getIdentifierNames() {
    return OCLC_IDENTIFIER_NAMES;
  }

  private String normalizeOclcValue(String value) {
    return WHITESPACE_REGEX.matcher(trim(value)).replaceAll("-");
  }

}
