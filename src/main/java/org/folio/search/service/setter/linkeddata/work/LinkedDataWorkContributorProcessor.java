package org.folio.search.service.setter.linkeddata.work;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.LinkedDataWorkContributorsInner;
import org.folio.search.domain.dto.LinkedDataWorkInstancesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataWorkContributorProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    var workContributors = ofNullable(linkedDataWork.getContributors()).stream().flatMap(Collection::stream);
    var instanceContributors = ofNullable(linkedDataWork.getInstances()).stream().flatMap(Collection::stream)
      .map(LinkedDataWorkInstancesInner::getContributors).filter(Objects::nonNull).flatMap(Collection::stream);
    return Stream.concat(workContributors, instanceContributors)
      .filter(Objects::nonNull)
      .map(LinkedDataWorkContributorsInner::getName)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

}
