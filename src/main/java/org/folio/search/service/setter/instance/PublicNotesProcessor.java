package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.InstanceNotes;
import org.folio.search.service.setter.FieldProcessor;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicNotesProcessor implements FieldProcessor<List<String>> {

  private final JsonConverter jsonConverter;

  @Override
  public List<String> getFieldValue(Map<String, Object> eventBody) {
    var notes = MapUtils.getObject(eventBody, "notes");
    if (notes == null) {
      return Collections.emptyList();
    }

    var instanceNotes = jsonConverter.convert(notes, new TypeReference<List<InstanceNotes>>() {});
    return instanceNotes.stream()
      .filter(note -> note.getStaffOnly() == null || !note.getStaffOnly())
      .map(InstanceNotes::getNote)
      .filter(Objects::nonNull)
      .collect(toList());
  }
}
