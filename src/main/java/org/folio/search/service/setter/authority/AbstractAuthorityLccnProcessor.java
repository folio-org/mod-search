package org.folio.search.service.setter.authority;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Authority;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.lccn.LccnNormalizer;

public abstract class AbstractAuthorityLccnProcessor extends AbstractAuthorityIdentifierProcessor {

  private final LccnNormalizer stringNormalizer;

  protected AbstractAuthorityLccnProcessor(ReferenceDataService referenceDataService,
                                           LccnNormalizer stringNormalizer) {
    super(referenceDataService);
    this.stringNormalizer = stringNormalizer;
  }

  @Override
  public Set<String> getFieldValue(Authority entity) {
    return getIdentifierValuesStream(entity)
      .map(stringNormalizer)
      .flatMap(Optional::stream)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
