package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;
import static org.folio.search.utils.SearchUtils.toSafeStream;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.InstanceNotes;
import org.folio.search.service.setter.FieldProcessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicNotesProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return toSafeStream(instance.getNotes())
      .filter(Objects::nonNull)
      .filter(note -> note.getStaffOnly() == null || !note.getStaffOnly())
      .map(InstanceNotes::getNote)
      .filter(Objects::nonNull)
      .collect(toCollection(LinkedHashSet::new));
  }
}
