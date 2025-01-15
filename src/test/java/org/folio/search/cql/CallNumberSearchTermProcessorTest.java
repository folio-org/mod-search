package org.folio.search.cql;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.search.cql.searchterm.CallNumberSearchTermProcessor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CallNumberSearchTermProcessorTest {

  private final CallNumberSearchTermProcessor callNumberSearchTermProcessor = new CallNumberSearchTermProcessor();

  @CsvSource({
    "prefix-callnumber-suffix, prefixcallnumbersuffix*",
    "TK5105.88815 . A58 2004 FT MEADE, tk510588815a582004ftmeade*",
    "TK5105.88815   /  A 58 2004 ft-MEADE+, tk510588815a582004ftmeade*",
    "++ TK5105.88815++, tk510588815*",
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void callNumberSearchTermProcessorCanNormalizeQuery(String query, String result) {
    var expectedQuery = callNumberSearchTermProcessor.getSearchTerm(query);
    assertThat(result).isEqualTo(expectedQuery);
  }
}
