package org.folio.search.service.setter.linkeddata.work;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstanceOnly;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataSortTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkSortTitleProcessor implements FieldProcessor<LinkedDataWork, String> {

  private final LinkedDataSortTitleProcessor linkedDataSortTitleProcessor;

  @Override
  public String getFieldValue(LinkedDataWork linkedDataWork) {
    var workTitles = ofNullable(linkedDataWork.getTitles()).stream().flatMap(Collection::stream);
    var instanceTitles = ofNullable(linkedDataWork.getInstances()).stream().flatMap(Collection::stream)
      .map(LinkedDataInstanceOnly::getTitles).filter(Objects::nonNull).flatMap(Collection::stream);
    var titles = Stream.concat(workTitles, instanceTitles).toList();
    return linkedDataSortTitleProcessor.getFieldValue(titles);
  }

}
