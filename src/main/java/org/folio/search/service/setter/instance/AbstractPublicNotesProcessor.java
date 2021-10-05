package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.folio.search.domain.dto.CirculationNote;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.folio.search.service.setter.FieldProcessor;

public abstract class AbstractPublicNotesProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    return Stream.concat(
      getCirculationNotes(instance)
        .filter(Objects::nonNull)
        .filter(note -> note.getStaffOnly() == null || !note.getStaffOnly())
        .map(CirculationNote::getNote)
        .filter(Objects::nonNull),
      getNotes(instance).filter(Objects::nonNull)
        .filter(note -> note.getStaffOnly() == null || !note.getStaffOnly())
        .map(Note::getNote)
        .filter(Objects::nonNull)
    ).collect(toCollection(LinkedHashSet::new));

  }

  /**
   * Returns {@link Stream} with {@link CirculationNote} objects from holding/item.
   *
   * @param instance instance object to analyze
   * @return {@link Stream} with {@link CirculationNote} object
   */
  protected Stream<CirculationNote> getCirculationNotes(Instance instance) {
    return Stream.empty();
  }

  /**
   * Returns {@link Stream} with {@link Note} objects from instance/holding/item.
   *
   * @param instance instance object to analyze
   * @return {@link Stream} with {@link Note} object
   */
  protected abstract Stream<Note> getNotes(Instance instance);
}
