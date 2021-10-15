package org.folio.search.service.setter.instance;

import static java.util.Collections.singletonList;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceAlternativeTitles;
import org.folio.search.integration.InstanceReferenceDataService;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UniformTitleProcessor implements FieldProcessor<Instance, Set<String>> {

  private final InstanceReferenceDataService referenceDataService;
  private final List<String> uniformTitleTypeNames = singletonList("Uniform Title");

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var uniformTitleIds = referenceDataService.fetchAlternativeTitleIds(uniformTitleTypeNames);
    return toStreamSafe(instance.getAlternativeTitles())
      .filter(title -> uniformTitleIds.contains(title.getAlternativeTitleTypeId()))
      .map(InstanceAlternativeTitles::getAlternativeTitle)
      .collect(toLinkedHashSet());
  }
}
