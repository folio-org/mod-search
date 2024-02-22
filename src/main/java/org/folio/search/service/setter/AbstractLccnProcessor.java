package org.folio.search.service.setter;

import static org.folio.search.utils.SearchUtils.extractLccnNumericPart;
import static org.folio.search.utils.SearchUtils.normalizeLccn;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Identifiers;
import org.folio.search.integration.ReferenceDataService;

public abstract class AbstractLccnProcessor<T> extends AbstractIdentifierProcessor<T> {

  private static final List<String> LCCN_IDENTIFIER_NAME = List.of("LCCN", "Canceled LCCN");

  protected AbstractLccnProcessor(ReferenceDataService referenceDataService) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
  }

  @Override
  public Set<String> getFieldValue(T entity) {
    return filterIdentifiersValue(getIdentifiers(entity)).stream()
      .flatMap(value -> Stream.of(normalizeLccn(value), extractLccnNumericPart(value)))
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  protected abstract List<Identifiers> getIdentifiers(T entity);
}
