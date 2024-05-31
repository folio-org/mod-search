package org.folio.search.service.setter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.utils.SearchUtils;

public abstract class AbstractLccnProcessor<T> extends AbstractIdentifierProcessor<T> {

  private static final List<String> LCCN_IDENTIFIER_NAME = List.of("LCCN", "Canceled LCCN");

  protected AbstractLccnProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
  }

  @Override
  public Set<String> getFieldValue(T entity) {
    return filterIdentifiersValue(getIdentifiers(entity)).stream()
      .map(SearchUtils::normalizeLccn)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  protected abstract List<Identifier> getIdentifiers(T entity);
}
