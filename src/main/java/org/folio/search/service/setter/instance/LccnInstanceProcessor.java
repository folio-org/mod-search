package org.folio.search.service.setter.instance;

import static org.folio.search.utils.SearchUtils.extractLccnNumericPart;
import static org.folio.search.utils.SearchUtils.normalizeLccn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;
import org.springframework.stereotype.Component;

/**
 * Instance identifier field processor, which normalizes LCCN numbers.
 */
@Component
public class LccnInstanceProcessor extends AbstractIdentifierProcessor<Instance> {

  private static final List<String> LCCN_IDENTIFIER_NAME = List.of("LCCN", "Canceled LCCN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public LccnInstanceProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return filterIdentifiersValue(instance.getIdentifiers()).stream()
      .flatMap(value -> Stream.of(normalizeLccn(value), extractLccnNumericPart(value)))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
