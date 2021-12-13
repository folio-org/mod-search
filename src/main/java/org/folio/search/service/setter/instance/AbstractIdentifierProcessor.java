package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.IDENTIFIER_TYPES;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.integration.InstanceReferenceDataService;
import org.folio.search.service.setter.FieldProcessor;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor implements FieldProcessor<Instance, Set<String>> {

  private final InstanceReferenceDataService identifierTypeCache;

  /**
   * Returns instance identifiers from event body by specified set of types.
   *
   * @param instance event body as map to process
   * @return {@link List} of {@link InstanceIdentifiers} objects
   */
  protected List<InstanceIdentifiers> getInstanceIdentifiers(Instance instance) {
    var identifierTypeIds = identifierTypeCache.fetchReferenceData(IDENTIFIER_TYPES, getIdentifierNames());
    if (identifierTypeIds.isEmpty()) {
      log.warn("Failed to provide identifiers [processor: {}, resourceId: '{}']",
        this.getClass().getSimpleName(), instance.getId());
    }

    return toStreamSafe(instance.getIdentifiers())
      .filter(identifier -> identifierTypeIds.contains(identifier.getIdentifierTypeId()))
      .collect(toList());
  }

  /**
   * Returns set of identifier names, which will be used in method {@link #getInstanceIdentifiers(Instance)}.
   *
   * @return {@link List} of {@link String} instance identifier type names.
   */
  protected abstract List<String> getIdentifierNames();
}
