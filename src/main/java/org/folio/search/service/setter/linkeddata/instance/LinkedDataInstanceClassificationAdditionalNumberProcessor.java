package org.folio.search.service.setter.linkeddata.instance;

import static java.util.Optional.ofNullable;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataWorkOnly;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataClassificationAdditionalNumberProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceClassificationAdditionalNumberProcessor
  implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataClassificationAdditionalNumberProcessor processor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    return ofNullable(linkedDataInstance.getParentWork())
      .map(LinkedDataWorkOnly::getClassifications)
      .stream()
      .flatMap(i -> processor.getFieldValue(i).stream())
      .collect(Collectors.toSet());
  }

}
