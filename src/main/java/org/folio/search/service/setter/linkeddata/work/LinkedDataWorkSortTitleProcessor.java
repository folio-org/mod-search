package org.folio.search.service.setter.linkeddata.work;

import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataSortTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkSortTitleProcessor implements FieldProcessor<LinkedDataWork, String> {

  private final LinkedDataSortTitleProcessor linkedDataSortTitleProcessor;

  @Override
  public String getFieldValue(LinkedDataWork linkedDataWork) {
    return linkedDataSortTitleProcessor.getFieldValue(linkedDataWork.getTitles());
  }

}
