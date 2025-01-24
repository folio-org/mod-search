package org.folio.search.utils;

import static org.apache.commons.lang3.StringUtils.compareIgnoreCase;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@UnitTest
class CallNumberUtilsTest {

  @DisplayName("getFieldValue_parameterized_comparePairs")
  @ParameterizedTest(name = "[{index}] cn1={0}, cn2={1}")
  @CsvFileSource(resources = {
    "/samples/cn-browse/cn-browse-common.csv",
    "/samples/cn-browse/cn-browse-lc-numbers.csv",
    "/samples/cn-browse/cn-browse-dewey-numbers.csv",
    "/samples/cn-browse/cn-browse-other-schema.csv"
  })
  void getFieldValue_comparedPairs_parameterized(String firstCallNumber, String secondCallNumber) {
    assertThat(compareIgnoreCase(firstCallNumber, secondCallNumber)).isNegative();

    var firstResult = CallNumberUtils.getCallNumberAsLong(firstCallNumber);
    var secondResult = CallNumberUtils.getCallNumberAsLong(secondCallNumber);

    assertThat(firstResult).isLessThan(secondResult).isNotNegative();
    assertThat(secondResult).isNotNegative();
  }

  @CsvSource({
    "aaa,AAA",
    "abc,ABC",
    "ab\\as,AB\\AS",
    "ab№as,AB AS"
  })
  @ParameterizedTest
  void normalizeEffectiveShelvingOrder_positive(String given, String expected) {
    var actual = CallNumberUtils.normalizeEffectiveShelvingOrder(given);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void normalizeEffectiveShelvingOrder_positive_forNull() {
    var actual = CallNumberUtils.normalizeEffectiveShelvingOrder(null);
    assertThat(actual).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("supportedCharactersDataset")
  void isSupportedCharacter_positive(char given) {
    var actual = CallNumberUtils.isSupportedCharacter(given);
    assertThat(actual).isTrue();
  }

  @ParameterizedTest
  @MethodSource("letterCharacterDataProvider")
  void getIntValue_positive_letters(char given) {
    var actual = CallNumberUtils.getIntValue(given, 0);
    assertThat(actual).isEqualTo(given - 42);
  }

  @ParameterizedTest
  @MethodSource("numericCharacterDataProvider")
  void getIntValue_positive_numbers(char given) {
    var actual = CallNumberUtils.getIntValue(given, 0);
    assertThat(actual).isEqualTo(given - 40);
  }

  @CsvSource({
    "' ',0", "$,0", "!,1", "#,2", "+,3", "',',4", "-,5", ".,6", "/,7", ":,18", ";,19",
    "=,20", "?,21", "@,22", "\\,49", "_,50", "~,51"
  })
  @ParameterizedTest
  void getIntValue_positive_otherCharacters(char given, int expected) {
    var actual = CallNumberUtils.getIntValue(given, 0);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getEffectiveCallNumber_positive() {
    var actual = CallNumberUtils.getEffectiveCallNumber("prefix", "cn", null);
    assertThat(actual).isEqualTo("prefix cn");
  }

  @Test
  void getNormalizedCallNumber_positive() {
    var actual = CallNumberUtils.normalizeCallNumberComponents(null, "94 NF 14/1:3792-3835", null);
    assertThat(actual).isEqualTo("94nf14137923835");
  }

  @Test
  void getNormalizedCallNumber_with_suffix_prefix_positive() {
    var actual = CallNumberUtils.normalizeCallNumberComponents("prefix", "94 NF 14/1:3792-3835", "suffix");
    assertThat(actual).isEqualTo("prefix94nf14137923835suffix");
  }

  @ParameterizedTest
  @MethodSource("provideCalculateFullCallNumberArguments")
  void calculateFullCallNumber_variousInputs(String callNumber, String volume, String enumeration, String chronology,
                                             String copyNumber, String suffix, String expected) {
    var actual =
      CallNumberUtils.calculateFullCallNumber(callNumber, volume, enumeration, chronology, copyNumber, suffix);
    assertThat(actual).isEqualTo(expected);
  }

  @NullAndEmptySource
  @ParameterizedTest
  void calculateFullCallNumber_throwExceptionIfCallNumberIsNullOrEmpty(String callNumber) {
    assertThrows(IllegalArgumentException.class, () -> CallNumberUtils.calculateFullCallNumber(callNumber,
      "volume", "enumeration", "chronology", "copyNumber", "suffix"));
  }

  private static Stream<Arguments> provideCalculateFullCallNumberArguments() {
    return Stream.of(
      Arguments.of("callNumber", "volume", "enumeration", "chronology", "copyNumber", "suffix",
        "callNumber suffix volume enumeration chronology copyNumber"),
      Arguments.of("callNumber", null, null, null, null, null, "callNumber"),
      Arguments.of("callNumber", "volume", null, null, null, null, "callNumber volume"),
      Arguments.of("callNumber", null, "enumeration", null, null, null, "callNumber enumeration"),
      Arguments.of("callNumber", null, null, "chronology", null, null, "callNumber chronology"),
      Arguments.of("callNumber", null, null, null, "copyNumber", null, "callNumber copyNumber"),
      Arguments.of("callNumber", null, null, null, null, "suffix", "callNumber suffix"),
      Arguments.of("callNumber", "", "", "", "", "", "callNumber"),
      Arguments.of("callNumber", "volume", "", "", "", "", "callNumber volume"),
      Arguments.of("callNumber", "", "enumeration", "", "", "", "callNumber enumeration"),
      Arguments.of("callNumber", "", "", "chronology", "", "", "callNumber chronology"),
      Arguments.of("callNumber", "", "", "", "copyNumber", "", "callNumber copyNumber"),
      Arguments.of("callNumber", "", "", "", "", "suffix", "callNumber suffix")
    );
  }

  private static Stream<Arguments> supportedCharactersDataset() {
    return StreamEx.<Arguments>empty()
      .append(letterCharacterDataProvider())
      .append(numericCharacterDataProvider())
      .append(otherCharactersDataProvider());
  }

  private static Stream<Arguments> letterCharacterDataProvider() {
    return IntStream.rangeClosed('A', 'Z').mapToObj(e -> arguments((char) e));
  }

  private static Stream<Arguments> numericCharacterDataProvider() {
    return IntStream.rangeClosed('0', '9').mapToObj(e -> arguments((char) e));
  }

  private static Stream<Arguments> otherCharactersDataProvider() {
    return ".,:;=-+~_/\\#@?!".chars().mapToObj(e -> arguments((char) e));
  }

}
