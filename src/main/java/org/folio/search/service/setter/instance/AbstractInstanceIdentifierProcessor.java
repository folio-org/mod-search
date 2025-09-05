package org.folio.search.service.setter.instance;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;

public abstract class AbstractInstanceIdentifierProcessor extends AbstractIdentifierProcessor<Instance> {

  protected AbstractInstanceIdentifierProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  protected List<Identifier> getIdentifiers(Instance entity) {
    return Optional.ofNullable(entity)
      .map(Instance::getIdentifiers)
      .orElse(Collections.emptyList());
  }
}
