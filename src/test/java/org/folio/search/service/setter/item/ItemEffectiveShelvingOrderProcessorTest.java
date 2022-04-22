package org.folio.search.service.setter.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import one.util.streamex.StreamEx;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class ItemEffectiveShelvingOrderProcessorTest {

  private final ItemEffectiveShelvingOrderProcessor
    itemEffectiveShelvingOrderProcessor = new ItemEffectiveShelvingOrderProcessor();

  @Test
  void getFieldValue_positive() {
    var instance = new Instance().items(List.of(
      new Item().effectiveShelvingOrder("A1"),
      new Item().effectiveShelvingOrder("F-132,23"),
      new Item().effectiveShelvingOrder("unknown"),
      new Item().effectiveShelvingOrder("[]](測試)")));
    var actual = itemEffectiveShelvingOrderProcessor.getFieldValue(instance);
    assertThat(actual).containsExactly("A1", "F-132,23", "UNKNOWN");
  }

  @CsvSource({
    ",",
    "aaa,AAA",
    "abc,ABC",
    "ab\\as,AB\\AS",
    "ab№as,AB AS"
  })
  @ParameterizedTest
  void normalizeEffectiveShelvingOrder_positive(String given, String expected) {
    var actual = ItemEffectiveShelvingOrderProcessor.normalizeValue(given);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("supportedCharactersDataset")
  void isSupportedCharacter_positive(char given) {
    var actual = ItemEffectiveShelvingOrderProcessor.isSupportedCharacter(given);
    assertThat(actual).isTrue();
  }

  @ParameterizedTest
  @MethodSource("letterCharacterDataProvider")
  void getIntValue_positive_letters(char given) {
    var actual = ItemEffectiveShelvingOrderProcessor.getIntValue(given, 0);
    assertThat(actual).isEqualTo(given - 42);
  }

  @ParameterizedTest
  @MethodSource("numericCharacterDataProvider")
  void getIntValue_positive_numbers(char given) {
    var actual = ItemEffectiveShelvingOrderProcessor.getIntValue(given, 0);
    assertThat(actual).isEqualTo(given - 40);
  }

  @CsvSource({
    "' ',0", "#,1", "$,2", "+,3", "',',4", "-,5", ".,6", "/,7", ":,18", ";,19",
    "=,20", "?,21", "@,22", "\\,49", "_,50", "~,51"
  })
  @ParameterizedTest
  void getIntValue_positive_otherCharacters(char given, int expected) {
    var actual = ItemEffectiveShelvingOrderProcessor.getIntValue(given, 0);
    assertThat(actual).isEqualTo(expected);
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
    return ".,:;=-+~_/\\#$@?".chars().mapToObj(e -> arguments((char) e));
  }
}
