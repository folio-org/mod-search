package org.folio.search.service.setter.linkeddata.instance;

import static java.util.Optional.ofNullable;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataWorkOnly;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.service.setter.linkeddata.common.LinkedDataSortTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceSortTitleProcessor implements FieldProcessor<LinkedDataInstance, String> {

  private final LinkedDataSortTitleProcessor linkedDataSortTitleProcessor;

  @Override
  public String getFieldValue(LinkedDataInstance linkedDataInstance) {
    var instanceTitles = ofNullable(linkedDataInstance.getTitles())
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull);
    var workTitles = ofNullable(linkedDataInstance.getParentWork())
      .map(LinkedDataWorkOnly::getTitles)
      .stream()
      .flatMap(Collection::stream);
    var titles = Stream.concat(instanceTitles, workTitles).toList();
    return linkedDataSortTitleProcessor.getFieldValue(titles);
  }

}
