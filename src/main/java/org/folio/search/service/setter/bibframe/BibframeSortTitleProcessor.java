package org.folio.search.service.setter.bibframe;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.Bibframe;
import org.folio.search.domain.dto.BibframeInstancesInner;
import org.folio.search.domain.dto.BibframeTitlesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class BibframeSortTitleProcessor implements FieldProcessor<Bibframe, String> {

  @Override
  public String getFieldValue(Bibframe bibframe) {
    var workTitles = ofNullable(bibframe.getTitles()).stream().flatMap(Collection::stream);
    var instanceTitles = ofNullable(bibframe.getInstances()).stream().flatMap(Collection::stream)
      .map(BibframeInstancesInner::getTitles).filter(Objects::nonNull).flatMap(Collection::stream);
    return Stream.concat(workTitles, instanceTitles)
      .filter(Objects::nonNull)
      .map(BibframeTitlesInner::getValue)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.joining());
  }

}
