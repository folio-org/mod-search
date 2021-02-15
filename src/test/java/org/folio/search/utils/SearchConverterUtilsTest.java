package org.folio.search.utils;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;
import org.elasticsearch.common.collect.List;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
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
