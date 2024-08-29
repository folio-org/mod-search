package org.folio.search.service.setter.linkeddata.instance;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataSortTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceSortTitleProcessor implements FieldProcessor<LinkedDataInstance, String> {

  private final LinkedDataSortTitleProcessor linkedDataSortTitleProcessor;

  @Override
  public String getFieldValue(LinkedDataInstance linkedDataInstance) {
    return linkedDataSortTitleProcessor.getFieldValue(linkedDataInstance.getTitles());
  }

}
