package org.folio.search.service.setter.linkeddata.work;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataContributorProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkContributorProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  private final LinkedDataContributorProcessor linkedDataContributorProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    var workContributors = ofNullable(linkedDataWork.getContributors())
      .stream()
      .flatMap(Collection::stream);
    var instanceContributors = ofNullable(linkedDataWork.getInstances())
      .stream()
      .flatMap(Collection::stream)
      .map(LinkedDataInstanceOnly::getContributors)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream);
    var contributors = Stream.concat(workContributors, instanceContributors).toList();
    return linkedDataContributorProcessor.getFieldValue(contributors);
  }

}
