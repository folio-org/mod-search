package org.folio.search.service.setter.instance;

import java.util.List;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.lccn.LccnNormalizer;
import org.springframework.stereotype.Component;

/**
 * Instance identifier field processor, which normalizes LCCN numbers.
 */
@Component
public class CanceledLccnInstanceProcessor extends AbstractInstanceLccnProcessor {

  private static final List<String> IDENTIFIER_NAMES = List.of("Canceled LCCN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   * @param stringNormalizer     {@link LccnNormalizer} bean
   */
  public CanceledLccnInstanceProcessor(ReferenceDataService referenceDataService, LccnNormalizer stringNormalizer) {
    super(referenceDataService, stringNormalizer);
  }

  @Override
  public List<String> getIdentifierNames() {
    return IDENTIFIER_NAMES;
  }
}
