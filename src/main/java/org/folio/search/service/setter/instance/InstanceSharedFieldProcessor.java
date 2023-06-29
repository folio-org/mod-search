package org.folio.search.service.setter.instance;

import org.folio.search.domain.dto.Instance;
import org.folio.search.service.setter.AbstractSharedFieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class InstanceSharedFieldProcessor extends AbstractSharedFieldProcessor<Instance> {

  @Override
  protected String getSource(Instance eventBody) {
    return eventBody.getSource();
  }
}
