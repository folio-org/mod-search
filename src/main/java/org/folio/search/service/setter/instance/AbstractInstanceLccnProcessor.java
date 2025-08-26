package org.folio.search.service.setter.instance;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.folio.search.domain.dto.Instance;
import org.folio.search.integration.folio.ReferenceDataService;
import org.folio.search.service.lccn.LccnNormalizer;

public abstract class AbstractInstanceLccnProcessor extends AbstractInstanceIdentifierProcessor {

  private final LccnNormalizer stringNormalizer;

  protected AbstractInstanceLccnProcessor(ReferenceDataService referenceDataService,
                                          LccnNormalizer stringNormalizer) {
    super(referenceDataService);
    this.stringNormalizer = stringNormalizer;
  }

  @Override
  public Set<String> getFieldValue(Instance entity) {
    return getIdentifierValuesStream(entity)
      .map(stringNormalizer)
      .flatMap(Optional::stream)
      .filter(Objects::nonNull)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
