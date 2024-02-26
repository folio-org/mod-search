package org.folio.search.utils;

import static org.folio.search.utils.ShelvingOrderCalculationHelper.calculate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class ShelvingOrderCalculationHelperTest {

  @Test
  void shouldCalculateLcNumber() {
    String input = "HD1691 .I5 1967";
    String expectedShelfKey = "HD 41691 I5 41967";

    String result = calculate(input, ShelvingOrderAlgorithmType.LC);

    assertEquals(expectedShelfKey, result);
  }

  @Test
  void shouldCalculateDeweyNumber() {
    String input = "302.55";
    String expectedShelfKey = "3302.55";

    String result = calculate(input, ShelvingOrderAlgorithmType.DEWEY);

    assertEquals(expectedShelfKey, result);
  }

  @Test
  void shouldCalculateDefaultNumber() {
    String input = "hd1691 ^I5 1967";
    String expectedShelfKey = "HD1691  I5 1967";

    String result = calculate(input, ShelvingOrderAlgorithmType.DEFAULT);

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
