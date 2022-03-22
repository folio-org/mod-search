package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;

@UnitTest
class EffectiveShelvingOrderTermProcessorTest {

  private final EffectiveShelvingOrderTermProcessor searchTermProcessor = new EffectiveShelvingOrderTermProcessor();

  @ParameterizedTest
  @ValueSource(strings = {
    "A 11", "DA 3880", "A 210", "ZA 3123", "DA 3880 O6",
    "DA 3880 O6 J72", "E 211 N52 VOL 14", "F 43733 L370 41992",
    "E 12.11 I12 288 D", "CE 16 B6713 X 41993", "3185.25 ",
    "3350.21", "3362.82 292 220", "3591.52 263 220", "3641.5943 M68 L",
    "4123", "4782", "K 11 M44 V 270 NO 11 16 41984 JAN JUNE 11"
  })
  void getSearchTerm_parameterized_validShelfKey(String given) {
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).isEqualTo(given);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "a1", "a 1", "DA880.19", "A10", "za123 a23",
    "DA880 o6 j18", "raw2", "isju2ng", "RAW 2", "T 22.1:866"
  })
  void getSearchTerm_parameterized_callNumber(String given) {
    var expected = new LCCallNumber(given).getShelfKey();
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"782", "123", "185.25", "350.21", "362.82/92 / 20",
    "591.52/63 / 20", "641.5943 M68l", "12", "11", "25", "1"})
  void getSearchTerm_parameterized_deweyDecimalNumbers(String given) {
    var expected = new DeweyCallNumber(given).getShelfKey();
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "3782", "3123", "3185.25", "3350.21", "3362.82 292 220",
    "3591.52 263 220", "3641.5943 M68 L"})
  void getSearchTerm_parameterized_validDeweyDecimalShelfKey(String given) {
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).isEqualTo(given);
  }

  @ParameterizedTest
  @ValueSource(strings = {"rack №1", "raw", "unknown"})
  void getSearchTerm_parameterized_freeText(String given) {
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).isEqualTo(given.toUpperCase(Locale.ROOT));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "   "})
  void getSearchTerm_parameterized_emptyValues(String searchTerm) {
    var actual = searchTermProcessor.getSearchTerm(searchTerm);
    assertThat(actual).isEqualTo(searchTerm);
  }
}
