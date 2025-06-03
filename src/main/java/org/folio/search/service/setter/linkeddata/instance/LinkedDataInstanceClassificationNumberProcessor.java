package org.folio.search.service.setter.linkeddata.instance;

import static java.util.Optional.ofNullable;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataWorkOnly;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataClassificationNumberProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceClassificationNumberProcessor
  implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataClassificationNumberProcessor processor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    return ofNullable(linkedDataInstance.getParentWork())
      .map(LinkedDataWorkOnly::getClassifications)
      .stream()
      .flatMap(i -> processor.getFieldValue(i).stream())
      .collect(Collectors.toSet());
  }
}
