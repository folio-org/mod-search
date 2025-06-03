package org.folio.search.service.setter.linkeddata.work;

import static java.util.Optional.ofNullable;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataClassificationNumberProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkClassificationNumberProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  private final LinkedDataClassificationNumberProcessor processor;

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    return ofNullable(linkedDataWork.getClassifications())
      .stream()
      .flatMap(i -> processor.getFieldValue(i).stream())
      .collect(Collectors.toSet());
  }
}
