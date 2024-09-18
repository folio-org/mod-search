package org.folio.search.service.setter.linkeddata.instance;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataLccnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceLccnProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataLccnProcessor linkedDataLccnProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    return linkedDataLccnProcessor.getFieldValue(linkedDataInstance.getIdentifiers());
  }

}
