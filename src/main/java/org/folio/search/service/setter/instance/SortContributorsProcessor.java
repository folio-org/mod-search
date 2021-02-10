package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.InstanceContributors;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class SortContributorsProcessor implements FieldProcessor<String> {
  private final JsonConverter jsonConverter;

  @Override
  public String getFieldValue(Map<String, Object> eventBody) {
    final var contributors = getContributors(eventBody);
    if (contributors.isEmpty()) {
      return null;
    }

    final var mainContributor = contributors.stream()
      .filter(contributor -> isTrue(contributor.getPrimary()))
      .findFirst()
      .orElse(contributors.get(0));

    return mainContributor.getName();
  }

  private List<InstanceContributors> getContributors(Map<String, Object> eventBody) {
    final var contributors = MapUtils.getObject(eventBody, "contributors");

    return ofNullable(jsonConverter.convert(contributors,
      new TypeReference<List<InstanceContributors>>() {}))
      .orElse(emptyList());
  }
}
