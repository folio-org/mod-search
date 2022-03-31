package org.folio.search.service.setter.instance;

import static java.util.Collections.singletonList;
import static org.folio.search.client.InventoryReferenceDataClient.ReferenceDataType.ALTERNATIVE_TITLE_TYPES;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.CollectionUtils.toStreamSafe;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceAlternativeTitles;
import org.folio.search.integration.ReferenceDataService;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class UniformTitleProcessor implements FieldProcessor<Instance, Set<String>> {

  private final ReferenceDataService referenceDataService;
  private final List<String> uniformTitleTypeNames = singletonList("Uniform Title");

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var uniformTitleIds = referenceDataService.fetchReferenceData(ALTERNATIVE_TITLE_TYPES, uniformTitleTypeNames);
    if (uniformTitleIds.isEmpty()) {
      log.warn("Failed to provide uniform titles [processor: {}, resourceId: '{}']",
        this.getClass().getSimpleName(), instance.getId());
    }

    return toStreamSafe(instance.getAlternativeTitles())
      .filter(title -> uniformTitleIds.contains(title.getAlternativeTitleTypeId()))
      .map(InstanceAlternativeTitles::getAlternativeTitle)
      .collect(toLinkedHashSet());
  }
}
