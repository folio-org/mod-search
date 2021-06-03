package org.folio.search.service.setter.instance;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toCollection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceNotes;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicNotesProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var notes = instance.getNotes();
    if (CollectionUtils.isEmpty(notes)) {
      return emptySet();
    }

    return notes.stream()
      .filter(Objects::nonNull)
      .filter(note -> note.getStaffOnly() == null || !note.getStaffOnly())
      .map(InstanceNotes::getNote)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
  }
}
