package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class SuDocCallNumberTest {

  @ParameterizedTest
  @MethodSource("parseableSuDocProvider")
  void parse_supportedSuDoc_examplesAreTokenized(String callNumber, String authorSymbol, String subordinateOffice,
                                                 String series, String suffix) {
    // Arrange
    var suDocCallNumber = new SuDocCallNumber(callNumber);

    // Act & Assert
    assertThat(suDocCallNumber.isValid()).isTrue();
    assertThat(suDocCallNumber.authorSymbol).isEqualTo(authorSymbol);
    assertThat(suDocCallNumber.subordinateOffice).isEqualTo(subordinateOffice);
    assertThat(suDocCallNumber.series).isEqualTo(series);
    assertThat(suDocCallNumber.subSeries).isNull();
    assertThat(suDocCallNumber.suffix).isEqualTo(suffix);
  }

  @Test
  void getShelfKey_suffixNumbersWithDifferentLengths_sortedNumerically() {
    // Arrange
    var shorterNumber = new SuDocCallNumber("I 19.42/4:9");
    var longerNumber = new SuDocCallNumber("I 19.42/4:10");

    // Act & Assert
    assertThat(shorterNumber.getShelfKey()).isLessThan(longerNumber.getShelfKey());
  }

  @Test
  void getShelfKey_twoDigitSuffixes_sortNumerically() {
    // Arrange
    var twoDigitSuffix = new SuDocCallNumber("A 13.28:W 63");
    var laterTwoDigitSuffix = new SuDocCallNumber("A 13.28:W 64");

    // Act & Assert
    assertThat(twoDigitSuffix.getShelfKey()).isLessThan(laterTwoDigitSuffix.getShelfKey());
  }

  @ParameterizedTest
  @MethodSource("congressionalSuDocProvider")
  void parse_congressionalStyleStem_keepsCommitteeStemInSeries(String callNumber, String series, String suffix) {
    // Arrange
    var suDocCallNumber = new SuDocCallNumber(callNumber);

    // Act & Assert
    assertThat(suDocCallNumber.isValid()).isTrue();
    assertThat(suDocCallNumber.authorSymbol).isEqualTo("Y");
    assertThat(suDocCallNumber.subordinateOffice).isEqualTo("4");
    assertThat(suDocCallNumber.series).isEqualTo(series);
    assertThat(suDocCallNumber.subSeries).isNull();
    assertThat(suDocCallNumber.suffix).isEqualTo(suffix);
  }

  @Test
  void getShelfKey_dateTokens_sortNumericallyWithinSuffix() {
    // Arrange
    var earlier = new SuDocCallNumber("L36.202:F15/990");
    var later = new SuDocCallNumber("L36.202:F15/991");

    // Act & Assert
    assertThat(earlier.getShelfKey()).isLessThan(later.getShelfKey());
  }

  @Test
  void getShelfKey_whitespaces_producesSameShelfKey() {
    // Arrange
    var withSpace = new SuDocCallNumber("Y 10.2: B 85 / 10");
    var withoutSpace = new SuDocCallNumber("Y10.2:B85/10");

    // Act & Assert
    assertThat(withSpace.getShelfKey()).isEqualTo(withoutSpace.getShelfKey());
  }

  @Test
  void getShelfKey_punctuationPrecedence_colonAndDashSortBeforeSlash() {
    // Arrange
    var withColon = new SuDocCallNumber("GS1.2:SO2");
    var withDash = new SuDocCallNumber("GS1.2-SO2");
    var withSlash = new SuDocCallNumber("GS1.2/SO2");

    // Act & Assert
    assertThat(withColon.getShelfKey()).isLessThan(withSlash.getShelfKey());
    assertThat(withColon.getShelfKey()).isLessThan(withDash.getShelfKey());
    assertThat(withDash.getShelfKey()).isLessThan(withSlash.getShelfKey());
  }

  @Test
  void getShelfKey_exampleSequence_matchesExpectedSortOrder() {
    // Arrange
    var expectedOrder = List.of(
      "J29.2:D84/982",
      "J29.2:D84/2",
      "L36.202:F15/990",
      "L36.202:F15/991",
      "L36.202:F15/2",
      "L37.s:Oc1/2/991",
      "L37.2:Oc1/2/conversion",
      "T22.19:M54",
      "T22.19:M54/990",
      "T22.19/2:P94",
      "T22.19/2:P94/2",
      "Y4.F76/2:Af8/12",
      "Y4.F76/2:Af8/12/rev."
    );

    // Act
    var actualOrder = expectedOrder.stream()
      .sorted(Comparator.comparing(callNumber -> new SuDocCallNumber(callNumber).getShelfKey()))
      .toList();

    // Assert
    assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
  }

  private static Stream<Arguments> parseableSuDocProvider() {
    return Stream.of(
      Arguments.of("A 13.28:W 63", "A", "13", ".28", "W 63"),
      Arguments.of("C 3.186:P-20/180", "C", "3", ".186", "P-20/180"),
      Arguments.of("J 1.14/7:UN 3/992", "J", "1", ".14/7", "UN 3/992")
    );
  }

  private static Stream<Arguments> congressionalSuDocProvider() {
    return Stream.of(
      Arguments.of("Y 4.AG 8/1:EC 7", ".AG 8/1", "EC 7"),
      Arguments.of("Y 4.EN 2:S.HRG.107-24", ".EN 2", "S.HRG.107-24")
    );
  }
}
