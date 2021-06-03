package org.folio.search.service.setter.instance;

import static org.apache.commons.lang3.BooleanUtils.isTrue;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class SortContributorsProcessor implements FieldProcessor<Instance, String> {

  @Override
  public String getFieldValue(Instance instance) {
    var contributors = instance.getContributors();
    if (CollectionUtils.isEmpty(contributors)) {
      return null;
    }

    final var mainContributor = contributors.stream()
      .filter(contributor -> isTrue(contributor.getPrimary()))
      .findFirst()
      .orElse(contributors.get(0));

    return mainContributor.getName();
  }
}
