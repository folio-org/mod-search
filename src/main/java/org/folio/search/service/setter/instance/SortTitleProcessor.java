package org.folio.search.service.setter.instance;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class SortTitleProcessor implements FieldProcessor<Instance, String> {

  @Override
  public String getFieldValue(Instance instance) {
    var indexTitle = instance.getIndexTitle();
    return isNotBlank(indexTitle) ? indexTitle : defaultIfBlank(instance.getTitle(), null);
  }
}
