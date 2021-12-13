package org.folio.search.service.setter.authority;

import static java.util.stream.Collectors.toCollection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.AuthorityIdentifiers;
import org.folio.search.integration.InstanceReferenceDataService;
import org.springframework.stereotype.Component;

@Component
public class LccnProcessor extends AbstractIdentifierProcessor {

  static final List<String> LCCN_IDENTIFIER_NAMES =
    List.of("LCCN", "Control number", "Other standard identifier", "System control number");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link InstanceReferenceDataService} bean
   */
  public LccnProcessor(InstanceReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  public Set<String> getFieldValue(Authority authority) {
    return getAuthorityIdentifiers(authority).stream()
      .map(AuthorityIdentifiers::getValue)
      .filter(Objects::nonNull)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

  @Override
  protected List<String> getIdentifierNames() {
    return LCCN_IDENTIFIER_NAMES;
  }
}
