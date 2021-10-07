package org.folio.search.service.setter.item;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.CirculationNote;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.Note;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ItemPublicNotesProcessorTest {
  private final ItemPublicNotesProcessor itemPublicNotesProcessor = new ItemPublicNotesProcessor();

  @MethodSource("notesDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<String> expected) {
    var actual = itemPublicNotesProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> notesDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty items", instance(), emptyList()),
      arguments("item with note(staffOnly=null)", instance(item(note("value", null))), List.of("value")),
      arguments("item with note(staffOnly=false)", instance(item(note("value", false))), List.of("value")),
      arguments("item with null note", instance(item((Note) null)), emptyList()),
      arguments("item with null value in notes", instance(item((Note[]) null)), emptyList()),
      arguments("item with empty list in note", instance(new Item().notes(emptyList())), emptyList()),
      arguments("2 items with notes(staffOnly=false and null)",
        instance(item(note("value1", null)), item(note("value2", false))), List.of("value1", "value2")),
      arguments("item with note(staffOnly=true)", instance(item(note("value", true))), emptyList()),
      arguments("item with note(value=null,staffOnly=false)", instance(item(note(null, false))), emptyList()),
      arguments("items with duplicated notes", instance(
        item(note("note", false)), item(note("note", false)), item(note("note", true))), List.of("note")),
      arguments("items with notes and circulation notes", instance(item(
        List.of(note("regular note", false)),
        List.of(circulationNote("circ note", false), circulationNote("circ note2", false)))),
        List.of("circ note", "circ note2", "regular note")
      ),
      arguments("items with private notes and circulation notes", instance(item(
        List.of(note("regular note", true), note("regular note2", true)),
        List.of(circulationNote("circ note", false)))),
        List.of("circ note")
      ),
      arguments("item with notes and private circulation notes", instance(item(
        List.of(note("regular note", false), note("regular note2", false)),
        List.of(circulationNote("circ note", true), circulationNote("circ note2", true)))),
        List.of("regular note", "regular note2")
      ));
  }

  private static Instance instance(Item... items) {
    return new Instance().items(items != null ? Arrays.asList(items) : null);
  }

  private static Item item(Note... notes) {
    return new Item().notes(notes != null ? Arrays.asList(notes) : null);
  }

  private static Item item(List<Note> notes, List<CirculationNote> circulationNotes) {
    return new Item().notes(notes).circulationNotes(circulationNotes);
  }

  private static Note note(String value, Boolean staffOnly) {
    return new Note().note(value).staffOnly(staffOnly);
  }

  private static CirculationNote circulationNote(String value, Boolean staffOnly) {
    return new CirculationNote().note(value).staffOnly(staffOnly);
  }
}
