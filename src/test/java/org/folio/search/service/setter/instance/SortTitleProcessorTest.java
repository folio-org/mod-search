package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Instance;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class SortTitleProcessorTest {
  private final SortTitleProcessor sortTitleProcessor = new SortTitleProcessor();

  @MethodSource("instanceDataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={2}")
  void getFieldValue_parameterized(@SuppressWarnings("unused") String name, Instance eventBody, String expected) {
    var actual = sortTitleProcessor.getFieldValue(eventBody);
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> instanceDataProvider() {
    var title = "test-title";
    var indexTitle = "index-title";
    return Stream.of(
      arguments("title=null, indexTitle=null", instance(null, null), null),
      arguments("title=emptyValue, indexTitle=null", instance("", null), null),
      arguments("title=blankValue, indexTitle=null", instance("  ", null), null),
      arguments("title='test-title', indexTitle=blankValue", instance(title, ""), title),
      arguments("title='test-title', indexTitle=emptyValue", instance(title, "  "), title),
      arguments("title='test-value', indexTitle='index-title'", instance(title, indexTitle), indexTitle));
  }

  private static Instance instance(String title, String indexTitle) {
    return new Instance().title(title).indexTitle(indexTitle);
  }
}
