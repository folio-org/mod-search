package org.folio.search.service.setter.authority;

import static org.folio.search.utils.SearchUtils.extractLccnNumericPart;
import static org.folio.search.utils.SearchUtils.normalizeLccn;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
  public Set<String> getFieldValue(Authority authority) {
    return filterIdentifiersValue(authority.getIdentifiers()).stream()
      .flatMap(value -> Stream.of(normalizeLccn(value), extractLccnNumericPart(value)))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  protected List<Identifiers> getIdentifiers(Authority authority) {
    return Optional.ofNullable(authority)
      .map(Authority::getIdentifiers)
      .orElse(Collections.emptyList());
  }
}
