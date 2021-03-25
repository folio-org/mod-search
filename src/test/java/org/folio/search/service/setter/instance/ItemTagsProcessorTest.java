package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
class ItemTagsProcessorTest {

  @InjectMocks private ItemTagsProcessor itemTagsProcessor;
  @Spy private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @MethodSource("rawTagsDataProvider")
  @DisplayName("getFieldValue_positive")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_positive(Map<String, Object> eventBody, List<String> expected) {
    var actual = itemTagsProcessor.getFieldValue(eventBody);
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> rawTagsDataProvider() {
    return Stream.of(
      arguments(null, emptyList()),
      arguments(emptyMap(), emptyList()),
      arguments(mapOf("id", randomId()), emptyList()),
      arguments(mapOf("items", null), emptyList()),
      arguments(mapOf("items", emptyMap()), emptyList()),
      arguments(mapOf("items", emptyList()), emptyList()),
      arguments(mapOf("items", List.of(mapOf("tags", null))), emptyList()),
      arguments(mapOf("items", List.of(mapOf("tags", emptyMap()))), emptyList()),
      arguments(mapOf("items", List.of(mapOf("tags", mapOf("tagList", null)))), emptyList()),
      arguments(mapOf("items", List.of(mapOf("tags", mapOf("tagList", emptyList())))), emptyList()),
      arguments(mapOf("items", List.of(mapOf("tags", mapOf(
        "tagList", List.of("tag1", "tag2"))))), List.of("tag1", "tag2")),
      arguments(mapOf("items", List.of(
        mapOf("tags", mapOf("tagList", List.of("tag1", "tag2"))),
        mapOf("tags", mapOf("tagList", List.of("tag1", "tag2"))))),
        List.of("tag1", "tag2")),
      arguments(mapOf("items", List.of(mapOf("tags", mapOf("tagList", List.of(" tag2 "))))), List.of("tag2")),
      arguments(mapOf("items", List.of(mapOf("tags", mapOf("tagList", List.of("  "))))), emptyList()),
      arguments(mapOf("items", List.of(mapOf("tags", mapOf("tagList", singletonList((String) null))))), emptyList()),
      arguments(mapOf("holdings", List.of(mapOf("tags", mapOf("tagList", List.of("tag"))))), emptyList())
    );
  }
}
