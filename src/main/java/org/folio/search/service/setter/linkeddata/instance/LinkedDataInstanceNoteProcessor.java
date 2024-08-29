package org.folio.search.service.setter.linkeddata.instance;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataNoteProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceNoteProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataNoteProcessor linkedDataNoteProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    return linkedDataNoteProcessor.getFieldValue(linkedDataInstance.getNotes());
  }

}
