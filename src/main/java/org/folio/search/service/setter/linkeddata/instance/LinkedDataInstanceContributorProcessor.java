package org.folio.search.service.setter.linkeddata.instance;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataWorkOnly;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataContributorProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceContributorProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataContributorProcessor linkedDataContributorProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    var instanceContributors = ofNullable(linkedDataInstance.getContributors())
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull);
    var workContributors = ofNullable(linkedDataInstance.getParentWork())
      .map(LinkedDataWorkOnly::getContributors)
      .stream()
      .flatMap(Collection::stream);
    var contributors = Stream.concat(instanceContributors, workContributors).toList();
    return linkedDataContributorProcessor.getFieldValue(contributors);
  }

}
