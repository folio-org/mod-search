package org.folio.search.utils;

import static org.folio.search.utils.ShelvingOrderCalculationHelper.calculate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@UnitTest
class ShelvingOrderCalculationHelperTest {

  @Test
  void shouldCalculateLcNumber() {
    var input = "HD1691 .I5 1967";
    var expectedShelfKey = "HD 41691 I5 41967";

    var result = calculate(input, ShelvingOrderAlgorithmType.LC);

    assertEquals(expectedShelfKey, result);
  }

  @Test
  void shouldCalculateDeweyNumber() {
    var input = "302.55";
    var expectedShelfKey = "3302.55";

    var result = calculate(input, ShelvingOrderAlgorithmType.DEWEY);

    assertEquals(expectedShelfKey, result);
  }

  @Test
  void shouldCalculateNlmNumber() {
    var input = "WB 102.5 B62 2018";
    var expectedShelfKey = "WB 3102.5 B62 42018";

    var result = calculate(input, ShelvingOrderAlgorithmType.NLM);

    assertEquals(expectedShelfKey, result);
  }

  @Test
  void shouldCalculateSudocNumber() {
    var input = "G1.16 A63 41581";
    var expectedShelfKey = "G 11 216   !A 263 !541581";

    var result = calculate(input, ShelvingOrderAlgorithmType.SUDOC);

    assertEquals(expectedShelfKey, result);
  }

  @ParameterizedTest
  @EnumSource(value = ShelvingOrderAlgorithmType.class, mode = EnumSource.Mode.INCLUDE, names = {"DEFAULT", "OTHER"})
  void shouldCalculateNormalizedNumber(ShelvingOrderAlgorithmType type) {
    var input = "hd1691 ^I5 1967";
    var expectedShelfKey = "HD1691 ^I5 1967";

    var result = calculate(input, type);

    assertEquals(expectedShelfKey, result);
  }

  @Test
  void shouldThrowExceptionOnNullInput() {
    Exception exception = assertThrows(NullPointerException.class,
      () -> calculate(null, ShelvingOrderAlgorithmType.DEFAULT));

    assertEquals("input is marked non-null but is null", exception.getMessage());
  }

  @Test
  void shouldThrowExceptionOnNullAlgorithmType() {
    Exception exception = assertThrows(NullPointerException.class,
      () -> calculate("Valid Input", null));

    assertEquals("algorithmType is marked non-null but is null", exception.getMessage());
  }
}
