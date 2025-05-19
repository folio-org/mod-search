package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class PublicNotesProcessorTest {

  private final PublicNotesProcessor publicNotesProcessor = new PublicNotesProcessor();

  @MethodSource("notesDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<String> expected) {
    var actual = publicNotesProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> notesDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty notes", instanceWithNotes(), emptyList()),
      arguments("null notes", instanceWithNotes((Note) null), emptyList()),
      arguments("null value in notes", instanceWithNotes((Note[]) null), emptyList()),
      arguments("note(staffOnly=null)", instanceWithNotes(note("value", null)), List.of("value")),
      arguments("note(staffOnly=false)", instanceWithNotes(note("value", false)), List.of("value")),
      arguments("2 notes(staffOnly=false and null)",
        instanceWithNotes(note("value1", null), note("value2", false)), List.of("value1", "value2")),
      arguments("note(staffOnly=true)", instanceWithNotes(note("value", true)), emptyList()),
      arguments("note(value=null,staffOnly=false)", instanceWithNotes(note(null, false)), emptyList())
    );
  }

  private static Instance instanceWithNotes(Note... notes) {
    return new Instance().notes(notes != null ? Arrays.asList(notes) : null);
  }

  private static Note note(String value, Boolean staffOnly) {
    return new Note().note(value).staffOnly(staffOnly);
  }
}
