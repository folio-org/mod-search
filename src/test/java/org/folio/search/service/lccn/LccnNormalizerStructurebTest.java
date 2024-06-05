package org.folio.search.service.lccn;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class LccnNormalizerStructurebTest {
  private final LccnNormalizerStructureB lccnNormalizer = new LccnNormalizerStructureB();

  @Test
  void shouldRemoveSpaces() {
    assertThat(lccnNormalizer.apply("   2017000002")).isEqualTo(Optional.of("2017000002"));
    assertThat(lccnNormalizer.apply("2017000002 ")).isEqualTo(Optional.of("2017000002"));
    assertThat(lccnNormalizer.apply("2017 000002")).isEqualTo(Optional.of("2017000002"));
    assertThat(lccnNormalizer.apply(" 20 17000 002 ")).isEqualTo(Optional.of("2017000002"));
  }

  @ParameterizedTest
  @CsvSource({
    "2012425165//r75, 2012425165",
    "2022139101/AC/r932, 2022139101",
  })
  void shouldRemoveForwardSlash(String input, String expected) {
    assertThat(lccnNormalizer.apply(input)).isEqualTo(Optional.of(expected));
  }

  @Test
  void shouldRemoveHyphen() {
    assertThat(lccnNormalizer.apply("2022-890351")).isEqualTo(Optional.of("2022890351"));
  }

  @ParameterizedTest
  @CsvSource({
    "2011-89035, 2011089035",
    "2020-2, 2020000002",
  })
  void shouldNormalizeSerialNumber(String input, String expected) {
    assertThat(lccnNormalizer.apply(input)).isEqualTo(Optional.of(expected));
  }

  @Test
  void shouldNormalizeSpacesAndHyphenAndForwardSlash() {
    assertThat(lccnNormalizer.apply("  20 20-2 //r23/AC")).isEqualTo(Optional.of("2020000002"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "01234567891", // more than 10 digits
    "2020-2-34", // more than one hyphen
    "A017000002", // non-digit character
    "/2017000002", // slash in the beginning
    "", // empty string
    "202-0234334", // "-" is in third index (instead of forth)
    "2020-", // "-" is the last character
  })
  void shouldReturnEmptyOptionalWhenLccnIsNotValid(String toNormalize) {
    assertThat(lccnNormalizer.apply(toNormalize)).isEmpty();
  }
}
