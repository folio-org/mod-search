package org.folio.search.service.setter.instance;

import java.util.List;
import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.springframework.stereotype.Component;

@Component
public class IssnProcessor extends AbstractInstanceIdentifierProcessor {

  private static final List<String> IDENTIFIER_TYPE_NAMES = List.of("ISSN", "Invalid ISSN", "Linking ISSN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public IssnProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return getIdentifierValues(instance);
  }

  @Override
  public List<String> getIdentifierNames() {
    return IDENTIFIER_TYPE_NAMES;
  }
}
