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
import org.folio.search.service.setter.linkeddata.common.LinkedDataNoteProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataInstanceNoteProcessor implements FieldProcessor<LinkedDataInstance, Set<String>> {

  private final LinkedDataNoteProcessor linkedDataNoteProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataInstance linkedDataInstance) {
    var instanceNotes = ofNullable(linkedDataInstance.getNotes())
      .stream()
      .flatMap(Collection::stream)
      .filter(Objects::nonNull);
    var workNotes = ofNullable(linkedDataInstance.getParentWork())
      .map(LinkedDataWorkOnly::getNotes)
      .stream()
      .flatMap(Collection::stream);
    var contributors = Stream.concat(instanceNotes, workNotes).toList();
    return linkedDataNoteProcessor.getFieldValue(contributors);
  }

}
