package org.folio.search.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class StringEscaperTest {

  @ParameterizedTest
  @MethodSource("provideEscapeTestCases")
  void testEscape(String input, String expected) {
    var actual = StringEscaper.escape(input);
    assertEquals(expected, actual);
  }

  @Test
  void testEscape_negative() {
    assertThrows(IllegalArgumentException.class,
      () -> StringEscaper.escape("Input contains reserved control character \u0001"));
  }

  @ParameterizedTest
  @MethodSource("provideUnescapeTestCases")
  void testUnescape(String input, String expected) {
    var actual = StringEscaper.unescape(input);
    assertEquals(expected, actual);
  }

  private static Stream<Arguments> provideEscapeTestCases() {
    return Stream.of(
      Arguments.of("This is a test\\string.", "This is a test\u0001string."),
      Arguments.of("No special characters here.", "No special characters here."),
      Arguments.of("", ""),
      Arguments.of(null, null),
      Arguments.of("\\", "\u0001")
    );
  }

  private static Stream<Arguments> provideUnescapeTestCases() {
    return Stream.of(
      Arguments.of("This is a test\u0001string.", "This is a test\\string."),
      Arguments.of("No special characters here.", "No special characters here."),
      Arguments.of("", ""),
      Arguments.of(null, null),
      Arguments.of("\u0001", "\\")
    );
  }
}
