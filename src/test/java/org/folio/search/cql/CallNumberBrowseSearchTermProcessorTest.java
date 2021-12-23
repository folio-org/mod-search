package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.folio.search.service.setter.instance.CallNumberProcessor;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.marc4j.callnum.LCCallNumber;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberBrowseSearchTermProcessorTest {

  @InjectMocks private CallNumberBrowseSearchTermProcessor searchTermProcessor;
  @Mock private CallNumberProcessor callNumberProcessor;

  @ParameterizedTest
  @ValueSource(strings = {
    "A 11", "DA 3880", "A 210", "ZA 3123", "DA 3880 O6", "DA 3880 O6 J72", "E 211 N52 VOL 14",
    "F 43733 L370 41992", "E 12.11 I12 288 D", "CE 16 B6713 X 41993"})
  void getSearchTerm_parameterized_validShelfKey(String shelfKey) {
    when(callNumberProcessor.getCallNumberAsLong(shelfKey)).thenReturn(100L);
    var actual = searchTermProcessor.getSearchTerm(shelfKey);
    assertThat(actual).isEqualTo(100L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"a1", "a 1", "DA880.19", "A10", "za123 a23", "DA880 o6 j18"})
  void getSearchTerm_parameterized_callNumber(String callNumber) {
    when(callNumberProcessor.getCallNumberAsLong(new LCCallNumber(callNumber).getShelfKey())).thenReturn(100L);
    var actual = searchTermProcessor.getSearchTerm(callNumber);
    assertThat(actual).isEqualTo(100L);
  }
}
