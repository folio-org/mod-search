package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections.CollectionUtils.isEmpty;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.service.setter.FieldProcessor;

@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor implements FieldProcessor<Instance, Set<String>> {

  private final InstanceIdentifierTypeCache identifierTypeCache;

  /**
   * Returns instance identifiers from event body by specified set of types.
   *
   * @param instance event body as map to process
   * @return {@link List} of {@link InstanceIdentifiers} objects
   */
  protected List<InstanceIdentifiers> getInstanceIdentifiers(Instance instance) {
    var instanceIdentifiers = instance.getIdentifiers();
    if (isEmpty(instanceIdentifiers)) {
      return emptyList();
    }

    var identifierTypeIds = identifierTypeCache.fetchIdentifierIds(getIdentifierNames());
    return instanceIdentifiers.stream()
      .filter(instanceIdentifier -> identifierTypeIds.contains(instanceIdentifier.getIdentifierTypeId()))
      .collect(toList());
  }

  /**
   * Returns set of identifier names, which will be used in method {@link #getInstanceIdentifiers(Instance)}.
   *
   * @return {@link List} of {@link String} instance identifier type names.
   */
  protected abstract List<String> getIdentifierNames();
}
