package org.folio.search.service.setter.bibframe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class LccnNormalizerTest {
  private final LccnNormalizer lccnNormalizer = new LccnNormalizer();

  @Test
  void shouldRemoveSpaces() {
    assertThat(lccnNormalizer.normalizeLccn("   2017000002")).isEqualTo(Optional.of("2017000002"));
    assertThat(lccnNormalizer.normalizeLccn("2017000002 ")).isEqualTo(Optional.of("2017000002"));
    assertThat(lccnNormalizer.normalizeLccn("2017 000002")).isEqualTo(Optional.of("2017000002"));
    assertThat(lccnNormalizer.normalizeLccn(" 20 17000 002 ")).isEqualTo(Optional.of("2017000002"));
  }

  @Test
  void shouldRemoveForwardSlash() {
    assertThat(lccnNormalizer.normalizeLccn("2012425165//r75")).isEqualTo(Optional.of("2012425165"));
    assertThat(lccnNormalizer.normalizeLccn("2022139101/AC/r932")).isEqualTo(Optional.of("2022139101"));
  }

  @Test
  void shouldRemoveHyphen() {
    assertThat(lccnNormalizer.normalizeLccn("2022-890351")).isEqualTo(Optional.of("2022890351"));
  }

  @Test
  void shouldNormalizeSerialNumber() {
    assertThat(lccnNormalizer.normalizeLccn("2011-89035")).isEqualTo(Optional.of("2011089035"));
    assertThat(lccnNormalizer.normalizeLccn("2020-2")).isEqualTo(Optional.of("2020000002"));
  }

  @Test
  void shouldNormalizeSpacesAndHyphenAndForwardSlash() {
    assertThat(lccnNormalizer.normalizeLccn("  20 20-2 //r23/AC")).isEqualTo(Optional.of("2020000002"));
  }

  @Test
  void shouldReturnEmptyOptionalWhenLccnIsNotValid() {
    // more than 10 digits
    assertThat(lccnNormalizer.normalizeLccn("01234567891")).isEmpty();

    // more than one hyphen
    assertThat(lccnNormalizer.normalizeLccn("2020-2-34")).isEmpty();

    // non-digit character
    assertThat(lccnNormalizer.normalizeLccn("A017000002")).isEmpty();

    // slash in the beginning
    assertThat(lccnNormalizer.normalizeLccn("/2017000002")).isEmpty();

    // empty string
    assertThat(lccnNormalizer.normalizeLccn("")).isEmpty();

    // "-" is in third index (instead of forth)
    assertThat(lccnNormalizer.normalizeLccn("202-0234334")).isEmpty();

    // "-" is the last character
    assertThat(lccnNormalizer.normalizeLccn("2020-")).isEmpty();
  }
}
