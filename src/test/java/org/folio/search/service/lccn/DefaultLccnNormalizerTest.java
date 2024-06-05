package org.folio.search.service.lccn;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class DefaultLccnNormalizerTest {
  private final DefaultLccnNormalizer lccnNormalizer = new DefaultLccnNormalizer();

  @DisplayName("LCCN value normalization")
  @CsvSource({"n 1234,n1234", "  N  1234 ,n1234", "*1234,*1234", "1234*,1234*"})
  @ParameterizedTest(name = "[{index}] value={0}, expected={1}")
  void getLccnNormalized_parameterized(String value, String expected) {
    var normalized = lccnNormalizer.apply(value);
    assertThat(normalized).contains(expected);
  }
}
