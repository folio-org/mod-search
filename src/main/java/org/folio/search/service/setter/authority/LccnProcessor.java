package org.folio.search.service.setter.authority;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.Authority;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;
import org.springframework.stereotype.Component;

@Component
public class LccnProcessor extends AbstractIdentifierProcessor<Authority> {

  private static final List<String> LCCN_IDENTIFIER_NAME =
    List.of("LCCN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public LccnProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
  }

  @Override
  public Set<String> getFieldValue(Authority authority) {
    return filterIdentifiersValue(authority.getIdentifiers());
  }
}
