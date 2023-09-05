package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;
import org.testcontainers.shaded.org.apache.commons.lang3.StringUtils;

@UnitTest
class EffectiveShelvingOrderTermProcessorTest {

  private final EffectiveShelvingOrderTermProcessor searchTermProcessor = new EffectiveShelvingOrderTermProcessor();

  @ParameterizedTest
  @ValueSource(strings = {
    "T22.19:M54/990",
    "A 11", "DA 3880", "A 210", "ZA 3123", "DA 3880 O6",
    "DA 3880 O6 J72", "E 211 N52 VOL 14", "F 43733 L370 41992",
    "E 12.11 I12 288 D", "CE 16 B6713 X 41993",
    "K 11 M44 V 270 NO 11 16 41984 JAN JUNE 11"
  })
  void getSearchTerm_parameterized_validShelfKey(String given) {
    var expectedLC = new LCCallNumber(given).getShelfKey();
    var expectedSuDoc = Optional.of(new SuDocCallNumber(given));
    var actual = searchTermProcessor.getSearchTerm(given);

    assertThat(actual).contains(expectedLC);
    assertSuDocCallNumber(expectedSuDoc, actual);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "a1", "a 1", "DA880.19", "A10", "za123 a23",
    "DA880 o6 j18", "raw2", "isju2ng", "RAW 2", "T 22.1:866"
  })
  void getSearchTerm_parameterized_callNumber(String given) {
    var expectedLC = new LCCallNumber(given).getShelfKey();
    var expectedSuDoc = Optional.of(new SuDocCallNumber(given));
    var actual = searchTermProcessor.getSearchTerm(given);

    assertThat(actual).contains(expectedLC);
    assertSuDocCallNumber(expectedSuDoc, actual);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "QS 11 .GA1 E53 2005", "QS 11 .GA1 F875d 1999", "QS 11 .GA1 Q6 2012", "QS 11 .GI8 P235s 2006",
    "QS 124 B811m 1875", "QT 104 B736 2003", "QT 104 B736 2009", "WA 102.5 B62 2018", "WB 102.5 B62 2018",
    "WC 250 M56 2011", "WC 250 M6 2011"
    // uncomment the below sample call numbers once the necessary fix is added into marc4j library
    //"W 100 B5315 2018", "W 600 B5315 2020",
  })
  void getSearchTerm_parameterized_validNlmCallNumber(String given) {
    var expected = new NlmCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "QA 11 .GA1 E53 2005", "QB 11 .GA1 F875d 1999", "QC 11 .GA1 Q6 2012", "QD 11 .GI8 P235s 2006",
    "QE 124 B811m 1875", "QF 104 B736 2003", "QG 104 B736 2009", "AB 102.5 B5315 2018", "KDZ 102.5 B62 2018",
    "BC 102.5 B62 2018", "CD 250 M56 2011", "RD 250 M6 2011", "ZA 11 .GA1 E53 2005"
  })
  void getSearchTerm_parameterized_invalidNlmAndValidLcCallNumber(String given) {
    var lcCallNumber = new LCCallNumber(given);
    var nlmCallNumber = new NlmCallNumber(given);
    var expected = lcCallNumber.getShelfKey().trim();

    var actual = searchTermProcessor.getSearchTerm(given);

    assertFalse(nlmCallNumber.isValid());
    assertTrue(lcCallNumber.isValid());
    assertThat(actual).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"782", "123", "185.25", "350.21", "362.82/92 / 20",
                          "591.52/63 / 20", "641.5943 M68l", "12", "11", "25", "1"})
  void getSearchTerm_parameterized_deweyDecimalNumbers(String given) {
    var expected = new DeweyCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "3782", "3123", "3185.25", "3350.21", "3362.82 292 220",
    "3591.52 263 220", "3641.5943 M68 L", "4123", "4782"})
  void getSearchTerm_parameterized_validDeweyDecimalShelfKey(String given) {
    var expected = new DeweyCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).contains(expected);
  }

  @ParameterizedTest
  @CsvSource({"rack №1, RACK  1", "raw, RAW", "unknown, UNKNOWN"})
  void getSearchTerm_parameterized_freeText(String given, String expected) {
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "   "})
  void getSearchTerm_parameterized_emptyValues(String searchTerm) {
    var actual = searchTermProcessor.getSearchTerm(searchTerm);
    actual.removeIf(StringUtils::isEmpty);
    assertThat(actual).isEmpty();
  }

  void assertSuDocCallNumber(Optional<SuDocCallNumber> expectedSuDoc, List<String> actual) {
    expectedSuDoc
      .filter(CallNumber::isValid)
      .map(CallNumber::getShelfKey)
      .ifPresent(expected -> assertThat(actual).contains(expected));
  }
}
