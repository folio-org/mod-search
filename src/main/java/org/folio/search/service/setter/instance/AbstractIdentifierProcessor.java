package org.folio.search.service.setter.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.repository.cache.InstanceIdentifierTypeCache;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;

@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor implements FieldProcessor<List<String>> {

  protected final JsonConverter jsonConverter;
  private final InstanceIdentifierTypeCache identifierTypeCache;

  /**
   * Returns instance identifiers from event body by specified set of types.
   *
   * @param eventBody event body as map to process
   * @return {@link List} of {@link InstanceIdentifiers} objects
   */
  protected List<InstanceIdentifiers> getInstanceIdentifiers(Map<String, Object> eventBody) {
    var instanceIdentifiers = jsonConverter.convert(MapUtils.getObject(eventBody, "identifiers"),
      new TypeReference<List<InstanceIdentifiers>>() {});

    if (instanceIdentifiers == null || instanceIdentifiers.isEmpty()) {
      return Collections.emptyList();
    }

    var identifierTypeIds = identifierTypeCache.fetchIdentifierIds(getIdentifierNames());
    return instanceIdentifiers.stream()
      .filter(instanceIdentifier -> identifierTypeIds.contains(instanceIdentifier.getIdentifierTypeId()))
      .collect(Collectors.toList());
  }

  /**
   * Returns set of identifier names, which will be used in method {@link #getInstanceIdentifiers(Map)}.
   *
   * @return {@link List} of {@link String} instance identifier type names.
   */
  protected abstract List<String> getIdentifierNames();
}
