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
import org.folio.search.service.setter.linkeddata.common.LinkedDataTitleProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceTitleProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataTitleProcessor linkedDataTitleProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    var instanceTitles = ofNullable(linkedDataInstance.getTitles())
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull);
    var workTitles = ofNullable(linkedDataInstance.getParentWork())
      .map(LinkedDataWorkOnly::getTitles)
      .stream()
      .flatMap(Collection::stream);
    var titles = Stream.concat(instanceTitles, workTitles).toList();
    return linkedDataTitleProcessor.getFieldValue(titles);
  }

}
