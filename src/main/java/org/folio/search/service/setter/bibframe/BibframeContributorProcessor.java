package org.folio.search.service.setter.bibframe;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toCollection;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Bibframe;
import org.folio.search.domain.dto.BibframeContributorsInner;
import org.folio.search.domain.dto.BibframeInstancesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class BibframeContributorProcessor implements FieldProcessor<Bibframe, Set<String>> {

  @Override
  public Set<String> getFieldValue(Bibframe bibframe) {
    var workContributors = ofNullable(bibframe.getContributors()).stream().flatMap(Collection::stream);
    var instanceContributors = ofNullable(bibframe.getInstances()).stream().flatMap(Collection::stream)
      .map(BibframeInstancesInner::getContributors).filter(Objects::nonNull).flatMap(Collection::stream);
    return Stream.concat(workContributors, instanceContributors)
      .filter(Objects::nonNull)
      .map(BibframeContributorsInner::getName)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

}
