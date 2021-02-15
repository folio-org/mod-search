package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PublicNotesProcessorTest {

  @InjectMocks private PublicNotesProcessor publicNotesProcessor;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @MethodSource("testDataProvider")
  @DisplayName("should extract public notes from incoming event body")
  @ParameterizedTest(name = "[{index}] resourceBody={0}, expected={1}")
  void shouldExtractOnlyPublicNotes(Map<String, Object> eventBody, List<String> expected) {
    var actual = publicNotesProcessor.getFieldValue(eventBody);
    assertThat(actual).isEqualTo(expected);
    if (eventBody.get("notes") != null) {
      verify(jsonConverter).convert(eq(eventBody.get("notes")), any());
    }
  }

  private static Stream<Arguments> testDataProvider() {
    return Stream.of(
      arguments(mapOf("notes", List.of(mapOf("note", "value"))), List.of("value")),
      arguments(mapOf("notes", List.of(mapOf("staffOnly", false, "note", "value"))), List.of("value")),
      arguments(mapOf("notes", List.of(mapOf("staffOnly", null, "note", "value"))), List.of("value")),
      arguments(mapOf("notes", List.of(mapOf("staffOnly", true, "note", "value"))), emptyList()),
      arguments(mapOf("id", randomId()), emptyList()),
      arguments(mapOf("id", randomId(), "notes", null), emptyList()),
      arguments(mapOf("id", randomId(), "notes", emptyList()), emptyList()),
      arguments(mapOf("notes", mapOf("staffOnly", true, "note", "value")), emptyList())
    );
  }
}
