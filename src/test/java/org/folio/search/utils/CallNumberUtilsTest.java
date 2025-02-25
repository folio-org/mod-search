package org.folio.search.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

@UnitTest
class CallNumberUtilsTest {

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
  void calculateFullCallNumber_variousInputs(String callNumber, String suffix, String expected) {
    var actual =
      CallNumberUtils.calculateFullCallNumber(callNumber, suffix);
    assertThat(actual).isEqualTo(expected);
  }

  @NullAndEmptySource
  @ParameterizedTest
  void calculateFullCallNumber_throwExceptionIfCallNumberIsNullOrEmpty(String callNumber) {
    assertThrows(IllegalArgumentException.class, () -> CallNumberUtils.calculateFullCallNumber(callNumber, "suffix"));
  }

  private static Stream<Arguments> provideCalculateFullCallNumberArguments() {
    return Stream.of(
      Arguments.of("callNumber", "suffix", "callNumber suffix"),
      Arguments.of("callNumber", null, "callNumber"),
      Arguments.of("callNumber", "", "callNumber")
    );
  }

}
