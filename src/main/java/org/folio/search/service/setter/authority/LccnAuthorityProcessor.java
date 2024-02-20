package org.folio.search.service.setter.authority;

import static org.folio.search.utils.SearchUtils.extractLccnNumericPart;
import static org.folio.search.utils.SearchUtils.normalizeLccn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Authority;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;
import org.springframework.stereotype.Component;

@Component
public class LccnAuthorityProcessor extends AbstractIdentifierProcessor<Authority> {

  private static final List<String> LCCN_IDENTIFIER_NAME = List.of("LCCN", "Canceled LCCN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link ReferenceDataService} bean
   */
  public LccnAuthorityProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
  }

  @Override
  public Set<String> getFieldValue(Authority authority) {
    return filterIdentifiersValue(authority.getIdentifiers()).stream()
      .flatMap(value -> Stream.of(normalizeLccn(value), extractLccnNumericPart(value)))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
