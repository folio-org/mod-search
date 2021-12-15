package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.integration.InstanceReferenceDataService;
import org.folio.search.service.setter.AbstractIdentifierProcessor;
import org.springframework.stereotype.Component;

@Component
public class IssnProcessor extends AbstractIdentifierProcessor<Instance> {

  private static final List<String> ISSN_IDENTIFIER_NAMES = List.of("ISSN", "Invalid ISSN");

  /**
   * Used by dependency injection.
   *
   * @param referenceDataService {@link InstanceReferenceDataService} bean
   */
  public IssnProcessor(InstanceReferenceDataService referenceDataService) {
    super(referenceDataService, ISSN_IDENTIFIER_NAMES);
  }

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var identifierTypeIds = fetchIdentifierIdsFromCache();

    return toStreamSafe(instance.getIdentifiers())
      .filter(identifier -> identifierTypeIds.contains(identifier.getIdentifierTypeId()))
      .map(InstanceIdentifiers::getValue)
      .filter(Objects::nonNull)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }
}
