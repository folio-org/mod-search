package org.folio.search.service.setter.linkeddata.work;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataLccnProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkLccnProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  private final LinkedDataLccnProcessor linkedDataLccnProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    return ofNullable(linkedDataWork.getInstances())
      .stream()
      .flatMap(Collection::stream)
      .map(LinkedDataInstanceOnly::getIdentifiers)
      .flatMap(i -> linkedDataLccnProcessor.getFieldValue(i).stream())
      .collect(Collectors.toSet());
  }

}
