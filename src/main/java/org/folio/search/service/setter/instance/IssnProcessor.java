package org.folio.search.service.setter.instance;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;
import org.springframework.stereotype.Component;

@Component
public class IssnProcessor extends AbstractIdentifierProcessor<Instance> {

  private static final List<String> IDENTIFIER_TYPE_NAMES = List.of("ISSN", "Invalid ISSN", "Linking ISSN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public IssnProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, IDENTIFIER_TYPE_NAMES);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return filterIdentifiersValue(instance.getIdentifiers());
  }
}
