package org.folio.search.service.setter.holding;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Note;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class HoldingPublicNotesProcessorTest {
  private final HoldingPublicNotesProcessor holdingPublicNotesProcessor = new HoldingPublicNotesProcessor();

  @MethodSource("notesDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, List<String> expected) {
    var actual = holdingPublicNotesProcessor.getFieldValue(eventBody);
    assertThat(actual).containsExactlyElementsOf(expected);
  }

  private static Stream<Arguments> notesDataProvider() {
    return Stream.of(
      arguments("all empty fields", new Instance(), emptyList()),
      arguments("empty holdings", instance(), emptyList()),
      arguments("holding with note(staffOnly=null)", instance(holding(note("value", null))), List.of("value")),
      arguments("holding with note(staffOnly=false)", instance(holding(note("value", false))), List.of("value")),
      arguments("holding with null note", instance(holding((Note) null)), emptyList()),
      arguments("holding with null value in notes", instance(holding((Note[]) null)), emptyList()),
      arguments("holding with empty list in note", instance(new Holding().notes(emptyList())), emptyList()),
      arguments("2 holdings with notes(staffOnly=false and null)",
        instance(holding(note("value1", null)), holding(note("value2", false))), List.of("value1", "value2")),
      arguments("holding with note(staffOnly=true)", instance(holding(note("value", true))), emptyList()),
      arguments("holding with note(value=null,staffOnly=false)", instance(holding(note(null, false))), emptyList()),
      arguments("holdings with duplicated notes", instance(
        holding(note("note", false)), holding(note("note", false)), holding(note("note", true))), List.of("note"))
    );
  }

  private static Instance instance(Holding... holdings) {
    return new Instance().holdings(holdings != null ? Arrays.asList(holdings) : null);
  }

  private static Holding holding(Note... notes) {
    return new Holding().notes(notes != null ? Arrays.asList(notes) : null);
  }

  private static Note note(String value, Boolean staffOnly) {
    return new Note().note(value).staffOnly(staffOnly);
  }
}
