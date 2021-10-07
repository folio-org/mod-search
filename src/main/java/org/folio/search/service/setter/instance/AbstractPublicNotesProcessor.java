package org.folio.search.service.setter.instance;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.folio.search.domain.dto.CirculationNote;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.folio.search.service.setter.FieldProcessor;

public abstract class AbstractPublicNotesProcessor implements FieldProcessor<Instance, Set<String>> {

  @Override
  public Set<String> getFieldValue(Instance instance) {
    var result = new LinkedHashSet<String>();
    result.addAll(getNotesAsList(getNotes(instance), note -> getNote(note.getStaffOnly(), note.getNote())));
    result.addAll(getNotesAsList(getCirculationNotes(instance), note -> getNote(note.getStaffOnly(), note.getNote())));
    return result;
  }

  private static <T> List<String> getNotesAsList(Stream<T> notesStream, Function<T, String> func) {
    return notesStream.filter(Objects::nonNull).map(func).filter(Objects::nonNull).collect(toList());
  }

  private static String getNote(Boolean staffOnly, String value) {
    return staffOnly == null || !staffOnly ? value : null;
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
