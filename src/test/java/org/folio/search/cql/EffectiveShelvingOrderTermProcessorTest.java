package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.CallNumberUtils.normalizeEffectiveShelvingOrder;

import java.util.List;
import java.util.stream.Collectors;
import org.folio.spring.test.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.marc4j.callnum.CallNumber;
import org.marc4j.callnum.DeweyCallNumber;
import org.marc4j.callnum.LCCallNumber;
import org.marc4j.callnum.NlmCallNumber;

@UnitTest
class EffectiveShelvingOrderTermProcessorTest {

  private final EffectiveShelvingOrderTermProcessor searchTermProcessor = new EffectiveShelvingOrderTermProcessor();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {
    "T22.19:M54/990",
    "A 11", "DA 3880", "A 210", "ZA 3123", "DA 3880 O6",
    "DA 3880 O6 J72", "E 211 N52 VOL 14", "F 43733 L370 41992",
    "E 12.11 I12 288 D", "CE 16 B6713 X 41993",
    "K 11 M44 V 270 NO 11 16 41984 JAN JUNE 11",
    "a1", "a 1", "DA880.19", "A10", "za123 a23",
    "DA880 o6 j18", "raw2", "isju2ng", "RAW 2", "T 22.1:866 ",
    "QA 11 .GA1 E53 2005", "QB 11 .GA1 F875d 1999", "QC 11 .GA1 Q6 2012", "QD 11 .GI8 P235s 2006",
    "QE 124 B811m 1875", "QF 104 B736 2003", "QG 104 B736 2009", "AB 102.5 B5315 2018", "KDZ 102.5 B62 2018",
    "BC 102.5 B62 2018", "CD 250 M56 2011", "RD 250 M6 2011", "ZA 11 .GA1 E53 2005"
  })
  void getSearchTerm_lc(String given) {
    var expected = new LCCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given, "lc");

    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {
    "QS 11 .GA1 E53 2005", "QS 11 .GA1 F875d 1999", "QS 11 .GA1 Q6 2012", "QS 11 .GI8 P235s 2006",
    "QS 124 B811m 1875", "QT 104 B736 2003", "QT 104 B736 2009", "WA 102.5 B62 2018", "WB 102.5 B62 2018",
    "WC 250 M56 2011", "WC 250 M6 2011 "
    // uncomment the below sample call numbers once the necessary fix is added into marc4j library
    //"W 100 B5315 2018", "W 600 B5315 2020",
  })
  void getSearchTerm_nlm(String given) {
    var expected = new NlmCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given, "nlm");
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"782", "123", "185.25", "350.21", "362.82/92 / 20",
    "591.52/63 / 20", "641.5943 M68l", "12", "11", "25", "1",
    "3782", "3123", "3185.25", "3350.21", "3362.82 292 220",
    "3591.52 263 220", "3641.5943 M68 L", "4123", "4782", "396.300"})
  void getSearchTerm_dewey(String given) {
    var expected = new DeweyCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given, "dewey");
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"D1.3201 B34 41972", "D1.211 N52 VOL 14", "G1.16 A63 41581"})
  void getSearchTerm_sudoc(String given) {
    var expected = new SuDocCallNumber(given).getShelfKey().trim();
    var actual = searchTermProcessor.getSearchTerm(given, "sudoc");
    assertThat(actual).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"rack №1", "raw", " unknown", "3641.5943 M68 L ", "   "})
  void getSearchTerm(String given) {
    var expected = normalizeEffectiveShelvingOrder(given);
    var actual = searchTermProcessor.getSearchTerm(given);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  void getSearchTerms_getMultiple() {
    var given = "T22.19:M54/990";
    var callNumbers = List.of(new NlmCallNumber(given), new LCCallNumber(given),
      new DeweyCallNumber(given), new SuDocCallNumber(given));
    var expected = callNumbers.stream()
      .map(CallNumber::getShelfKey)
      .collect(Collectors.toList());
    expected.add(normalizeEffectiveShelvingOrder(given));
    var actual = searchTermProcessor.getSearchTerms(given);

    assertThat(callNumbers).extracting(CallNumber::isValid)
        .containsExactly(false, true, false, true);
    assertThat(actual).isEqualTo(expected);
  }
}
