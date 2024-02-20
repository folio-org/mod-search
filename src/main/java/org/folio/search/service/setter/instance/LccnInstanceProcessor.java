package org.folio.search.service.setter.instance;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractLccnProcessor;
import org.springframework.stereotype.Component;

/**
 * Instance identifier field processor, which normalizes LCCN numbers.
 */
@Component
public class LccnInstanceProcessor extends AbstractLccnProcessor<Instance> {

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public LccnInstanceProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  protected List<Identifiers> getIdentifiers(Instance instance) {
    return Optional.ofNullable(instance)
      .map(Instance::getIdentifiers)
      .orElse(Collections.emptyList());
  }
}
