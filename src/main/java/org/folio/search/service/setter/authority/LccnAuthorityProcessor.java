package org.folio.search.service.setter.authority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractLccnProcessor;
import org.springframework.stereotype.Component;

@Component
public class LccnAuthorityProcessor extends AbstractLccnProcessor<Authority> {

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public LccnAuthorityProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  protected List<Identifiers> getIdentifiers(Authority authority) {
    return Optional.ofNullable(authority)
      .map(Authority::getIdentifiers)
      .orElse(Collections.emptyList());
  }
}
