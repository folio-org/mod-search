package org.folio.search.cql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.folio.search.cql.searchterm.ClassificationNumberSearchTermProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ClassificationNumberSearchTermProcessorTest {

  private final ClassificationNumberSearchTermProcessor processor = new ClassificationNumberSearchTermProcessor();

  @ParameterizedTest
  @MethodSource("provideTermsForTesting")
  void processValidTerms(String input, String expected) {
    String result = processor.getSearchTerm(input);
    assertEquals(expected, result);
  }

  private static Stream<Arguments> provideTermsForTesting() {
    return Stream.of(
      Arguments.of("   ", "*"),
      Arguments.of("abc123", "abc123"),
      Arguments.of("*abc123", "*abc123"),
      Arguments.of("abc123*", "abc123*"),
      Arguments.of("*abc123*", "*abc123*"),
      Arguments.of(" abc#$ %123 ", "abc123")
    );
  }
}
