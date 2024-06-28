package org.folio.search.service.setter.linkeddata.work;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.LinkedDataWorkInstancesInner;
import org.folio.search.domain.dto.LinkedDataWorkTitlesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class LinkedDataWorkSortTitleProcessor implements FieldProcessor<LinkedDataWork, String> {

  @Override
  public String getFieldValue(LinkedDataWork linkedDataWork) {
    var workTitles = ofNullable(linkedDataWork.getTitles()).stream().flatMap(Collection::stream);
    var instanceTitles = ofNullable(linkedDataWork.getInstances()).stream().flatMap(Collection::stream)
      .map(LinkedDataWorkInstancesInner::getTitles).filter(Objects::nonNull).flatMap(Collection::stream);
    return Stream.concat(workTitles, instanceTitles)
      .filter(Objects::nonNull)
      .map(LinkedDataWorkTitlesInner::getValue)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining());
  }

}
