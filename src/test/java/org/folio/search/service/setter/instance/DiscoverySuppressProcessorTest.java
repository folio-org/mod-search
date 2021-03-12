package org.folio.search.service.setter.instance;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class DiscoverySuppressProcessorTest {

  private DiscoverySuppressProcessor discoverySuppressProcessor;

  @BeforeEach
  void setUp() {
    discoverySuppressProcessor = new DiscoverySuppressProcessor();
  }

  @DisplayName("getFieldValue_positive")
  @MethodSource("getFieldValueDataProvider")
  @ParameterizedTest(name = "[{index}] initial={0}, expected={1}")
  void getFieldValue_positive(Map<String, Object> eventBody, Boolean expected) {
    var actual = discoverySuppressProcessor.getFieldValue(eventBody);
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> getFieldValueDataProvider() {
    return Stream.of(
      arguments(emptyMap(), false),
      arguments(mapOf("id", randomId()), false),
      arguments(mapOf("discoverySuppress", null), false),
      arguments(mapOf("discoverySuppress", false), false),
      arguments(mapOf("discoverySuppress", true), true)
    );
  }
}
