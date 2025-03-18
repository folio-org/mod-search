package org.folio.search.service.setter.instance;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.lccn.StringNormalizer;
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
   * @param stringNormalizer {@link StringNormalizer} bean
   */
  public LccnInstanceProcessor(ReferenceDataService referenceDataService, StringNormalizer stringNormalizer) {
    super(referenceDataService, stringNormalizer);
  }

  @Override
  protected List<Identifier> getIdentifiers(Instance instance) {
    return Optional.ofNullable(instance)
      .map(Instance::getIdentifiers)
      .orElse(Collections.emptyList());
  }
}
