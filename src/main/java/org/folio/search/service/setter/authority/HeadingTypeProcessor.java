package org.folio.search.service.setter.authority;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.folio.search.model.metadata.DistinctiveFieldDescription;
import org.folio.search.service.metadata.SearchFieldProvider;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HeadingTypeProcessor implements FieldProcessor<Map<String, Object>, String> {

  private final SearchFieldProvider searchFieldProvider;

  @Override
  public String getFieldValue(Map<String, Object> eventBody) {
    return eventBody.entrySet().stream()
      .map(this::getHeadingTypeForField)
      .flatMap(Optional::stream)
      .findFirst()
      .orElse(null);
  }

  public Optional<String> getHeadingTypeForField(Entry<String, Object> entry) {
    if (ObjectUtils.isEmpty(entry.getValue())) {
      return Optional.empty();
    }

    return searchFieldProvider.getPlainFieldByPath(AUTHORITY_RESOURCE, entry.getKey())
      .filter(DistinctiveFieldDescription.class::isInstance)
      .map(DistinctiveFieldDescription.class::cast)
      .map(DistinctiveFieldDescription::getHeadingType);
  }
}
