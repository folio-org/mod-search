package org.folio.search.service.setter.linkeddata.instance;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataIsbnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceIsbnProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataIsbnProcessor linkedDataIsbnProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    return linkedDataIsbnProcessor.getFieldValue(linkedDataInstance.getIdentifiers());
  }
}
