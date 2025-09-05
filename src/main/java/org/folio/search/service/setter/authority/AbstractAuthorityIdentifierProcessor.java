package org.folio.search.service.setter.authority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;

public abstract class AbstractAuthorityIdentifierProcessor extends AbstractIdentifierProcessor<Authority> {

  protected AbstractAuthorityIdentifierProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  protected List<Identifier> getIdentifiers(Authority entity) {
    return Optional.ofNullable(entity)
      .map(Authority::getIdentifiers)
      .orElse(Collections.emptyList());
  }
}
