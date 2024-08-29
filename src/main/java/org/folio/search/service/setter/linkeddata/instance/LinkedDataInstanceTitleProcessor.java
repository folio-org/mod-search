package org.folio.search.service.setter.linkeddata.instance;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceTitleProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataTitleProcessor linkedDataTitleProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    return linkedDataTitleProcessor.getFieldValue(linkedDataInstance.getTitles());
  }

}
