package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class SearchConverterUtilsTest {

  @MethodSource("getValueByPathProvider")
  @DisplayName("should receive value by path")
  @ParameterizedTest(name = "[{index}] path={1}, expected={2}")
  void getValueByPath(String path, Map<String, Object> document, Object expected) {
    var actual = SearchConverterUtils.getMapValueByPath(path, document);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getEventPayload_newValue() {
    var actual = SearchConverterUtils.getEventPayload(resourceEvent(RESOURCE_NAME, emptyMap()));
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void getEventPayload_oldValue() {
    var actual = SearchConverterUtils.getEventPayload(resourceEvent(RESOURCE_NAME, null).old(emptyMap()));
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void getNewAsMap_positive() {
    var newData = mapOf(ID_FIELD, RESOURCE_ID);
    var actual = SearchConverterUtils.getNewAsMap(resourceEvent(RESOURCE_NAME, newData));
    assertThat(actual).isEqualTo(newData);
  }

  @Test
  void getNewAsMap_negative() {
    var actual = SearchConverterUtils.getNewAsMap(resourceEvent(RESOURCE_NAME, null));
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void getOldAsMap_positive() {
    var oldData = mapOf(ID_FIELD, RESOURCE_ID);
    var actual = SearchConverterUtils.getOldAsMap(resourceEvent(RESOURCE_NAME, null).old(oldData));
    assertThat(actual).isEqualTo(oldData);
  }

  @Test
  void getOldAsMap_negative() {
    var actual = SearchConverterUtils.getOldAsMap(resourceEvent(RESOURCE_NAME, null));
    assertThat(actual).isEqualTo(emptyMap());
  }

  @Test
  void getResourceId_positive() {
    var resourceEvent = resourceEvent(INSTANCE_RESOURCE, Map.of("id", RESOURCE_ID));
    var actual = SearchConverterUtils.getResourceEventId(resourceEvent);
    assertThat(actual).isEqualTo(RESOURCE_ID);
  }

  private static Stream<Arguments> getValueByPathProvider() {
    return Stream.of(
      arguments("$.languages", emptyMap(), null),
      arguments("$.languages", mapOf("key", "value"), null),
      arguments("$.languages.value", mapOf("languages", "eng"), null),
      arguments("$.languages.value", mapOf("languages", List.of("eng", "rus")), null),
      arguments("languages", mapOf("languages", "eng"), "eng"),
      arguments("$.languages", mapOf("languages", "eng"), "eng"),
      arguments("$.languages", mapOf("languages", List.of("eng", "rus")), List.of("eng", "rus")),
      arguments("$.languages.value", mapOf("languages", List.of(
        mapOf("value", "rus"), mapOf("value", "eng"))), List.of("rus", "eng")),
      arguments("$.languages", mapOf("languages", List.of(
        mapOf("value", "rus"), mapOf("value", "eng"))), List.of(mapOf("value", "rus"), mapOf("value", "eng")))
    );
  }
}
