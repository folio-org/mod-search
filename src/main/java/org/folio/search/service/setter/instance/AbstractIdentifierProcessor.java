package org.folio.search.service.setter.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.InstanceIdentifiers;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;

@RequiredArgsConstructor
public abstract class AbstractIdentifierProcessor implements FieldProcessor<List<String>> {

  protected final JsonConverter jsonConverter;

  protected List<InstanceIdentifiers> getInstanceIdentifiers(Map<String, Object> eventBody) {
    var identifiers = MapUtils.getObject(eventBody, "identifiers");
    if (identifiers == null) {
      return Collections.emptyList();
    }
    List<InstanceIdentifiers> convert = jsonConverter.convert(identifiers, new TypeReference<>() {});
    var identifierTypeIds = getIdentifierTypeIds();
    return convert.stream()
      .filter(instanceIdentifier -> identifierTypeIds.contains(instanceIdentifier.getIdentifierTypeId()))
      .collect(Collectors.toList());
  }

  protected abstract Set<String> getIdentifierTypeIds();
}
