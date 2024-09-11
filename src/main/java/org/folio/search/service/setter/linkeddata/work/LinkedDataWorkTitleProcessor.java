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
import org.folio.search.service.setter.linkeddata.common.LinkedDataTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkTitleProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  private final LinkedDataTitleProcessor linkedDataTitleProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    var workTitles = ofNullable(linkedDataWork.getTitles())
      .stream()
      .flatMap(Collection::stream);
    var instTitles = ofNullable(linkedDataWork.getInstances())
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataInstanceOnly::getTitles)
      .flatMap(Collection::stream);
    var titles = Stream.concat(workTitles, instTitles).toList();
    return linkedDataTitleProcessor.getFieldValue(titles);
  }

}
