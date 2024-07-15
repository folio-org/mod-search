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
import org.folio.search.domain.dto.LinkedDataWorkInstancesInner;
import org.folio.search.domain.dto.LinkedDataWorkTitlesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataWorkTitleProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    var workTitles = ofNullable(linkedDataWork.getTitles()).stream().flatMap(Collection::stream);
    var instTitles = ofNullable(linkedDataWork.getInstances()).stream().flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataWorkInstancesInner::getTitles)
      .filter(Objects::nonNull)
      .flatMap(Collection::stream);
    return Stream.concat(workTitles, instTitles)
      .filter(Objects::nonNull)
      .map(LinkedDataWorkTitlesInner::getValue)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

}
