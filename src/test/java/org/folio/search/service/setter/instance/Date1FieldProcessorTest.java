package org.folio.search.service.setter.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.folio.search.domain.dto.Dates;
import org.folio.search.domain.dto.Instance;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class Date1FieldProcessorTest {

  private final Date1FieldProcessor date1FieldProcessor = new Date1FieldProcessor();

  @MethodSource("date1DataProvider")
  @DisplayName("getFieldValue_parameterized")
  @ParameterizedTest(name = "[{index}] instance with {0}, expected={1}")
  void getFieldValue_parameterized(Instance eventBody, Short expected) {
    var actual = date1FieldProcessor.getFieldValue(eventBody);
    assertThat(actual).isEqualTo(expected);
  }

  private static Stream<Arguments> date1DataProvider() {
    return Stream.of(
      arguments(instance("1999"), Short.valueOf("1999")),
      arguments(instance("199u"), Short.valueOf("1990")),
      arguments(instance("20u2"), Short.valueOf("2002")),
      arguments(instance("20uu"), Short.valueOf("2000")),
      arguments(instance("199"), Short.valueOf("199")),
      arguments(instance("1u9"), Short.valueOf("109")),
      arguments(instance("1"), Short.valueOf("1")),
      arguments(instance("19999"), Short.valueOf("0")),
      arguments(instance("19k5"), Short.valueOf("0")),
      arguments(new Instance(), Short.valueOf("0"))
    );
  }

  private static Instance instance(String date1) {
    return new Instance().dates(new Dates().date1(date1));
  }
}
