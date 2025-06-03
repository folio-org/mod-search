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
import org.folio.search.service.setter.linkeddata.common.LinkedDataNoteProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkedDataWorkNoteProcessor implements FieldProcessor<LinkedDataWork, Set<String>> {

  private final LinkedDataNoteProcessor linkedDataNoteProcessor;

  @Override
  public Set<String> getFieldValue(LinkedDataWork linkedDataWork) {
    var workNotes = ofNullable(linkedDataWork.getNotes())
      .stream()
      .flatMap(Collection::stream);
    var instanceNotes = ofNullable(linkedDataWork.getInstances()).stream().flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .map(LinkedDataInstanceOnly::getNotes)
      .flatMap(Collection::stream);
    var notes = Stream.concat(workNotes, instanceNotes).toList();
    return linkedDataNoteProcessor.getFieldValue(notes);
  }
}
