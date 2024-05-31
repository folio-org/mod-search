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
import org.folio.search.domain.dto.BibframeInstancesInner;
import org.folio.search.domain.dto.BibframeTitlesInner;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
public class BibframeTitleProcessor implements FieldProcessor<Bibframe, Set<String>> {

  @Override
  public Set<String> getFieldValue(Bibframe bibframe) {
    var workTitles = ofNullable(bibframe.getTitles()).stream().flatMap(Collection::stream);
    var instTitles = ofNullable(bibframe.getInstances()).stream().flatMap(Collection::stream).filter(Objects::nonNull)
      .map(BibframeInstancesInner::getTitles).filter(Objects::nonNull).flatMap(Collection::stream);
    return Stream.concat(workTitles, instTitles)
      .filter(Objects::nonNull)
      .map(BibframeTitlesInner::getValue)
      .filter(StringUtils::isNotBlank)
      .map(String::trim)
      .collect(toCollection(LinkedHashSet::new));
  }

}
