package org.folio.search.service.reindex;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@UnitTest
class RangeGeneratorTest {

  @CsvSource({
    "2,000001d8825cc3beb7e2cee2223565e17b26fac8",
    "3,000001d8825cc3beb7e2cee2223565e17b26fac8",
    "4,000001d8825cc3beb7e2cee2223565e17b26fac8",
    "2,51703becc548d684616abd8ef7286917b7f7922d",
    "3,51703becc548d684616abd8ef7286917b7f7922d",
    "4,51703becc548d684616abd8ef7286917b7f7922d",
    "2,ab7d50afb15f9474bd499cc2e9688c412ae11d48",
    "3,ab7d50afb15f9474bd499cc2e9688c412ae11d48",
    "4,ab7d50afb15f9474bd499cc2e9688c412ae11d48",
    "2,e175fc5b818e1092be59370632c95fe590980f3d",
    "3,e175fc5b818e1092be59370632c95fe590980f3d",
    "4,e175fc5b818e1092be59370632c95fe590980f3d",
    "2,fffffd04267fd060893a57167a54e2ad97432c04",
    "3,fffffd04267fd060893a57167a54e2ad97432c04",
    "4,fffffd04267fd060893a57167a54e2ad97432c04"
  })
  @ParameterizedTest(name = "generate hex ranges for length: {0}, hexValue: {1}")
  void shouldGenerateHexRanges(int length, String hexValue) {
    var ranges = RangeGenerator.createHexRanges(length);

    assertThat(ranges)
      .satisfies(hexRanges ->
        assertTrue(
          hexRanges.stream()
          .anyMatch(range -> hexValue.compareTo(range.lowerBound()) > 0 && hexValue.compareTo(range.upperBound()) < 0)
        )
      )
      .extracting(RangeGenerator.Range::lowerBound, RangeGenerator.Range::upperBound)
      .startsWith(tuple(repeat("0", length), repeat("0", length - 1) + "1"))
      .endsWith(tuple(repeat("f", length), repeat("x", length)));
  }
}
