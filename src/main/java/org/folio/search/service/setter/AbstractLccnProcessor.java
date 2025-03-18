package org.folio.search.service.setter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Identifier;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.lccn.StringNormalizer;

public abstract class AbstractLccnProcessor<T> extends AbstractIdentifierProcessor<T> {

  private static final List<String> LCCN_IDENTIFIER_NAME = List.of("LCCN", "Canceled LCCN");
  private final StringNormalizer stringNormalizer;

  protected AbstractLccnProcessor(ReferenceDataService referenceDataService, StringNormalizer stringNormalizer) {
    super(referenceDataService, LCCN_IDENTIFIER_NAME);
    this.stringNormalizer = stringNormalizer;
  }

  @Override
  public Set<String> getFieldValue(T entity) {
    return filterIdentifiersValue(getIdentifiers(entity)).stream()
      .map(stringNormalizer)
      .flatMap(Optional::stream)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  protected abstract List<Identifier> getIdentifiers(T entity);
}
