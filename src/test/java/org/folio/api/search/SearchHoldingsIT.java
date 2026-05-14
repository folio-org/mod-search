package org.folio.api.search;

import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public abstract class SearchHoldingsIT extends BaseSharedTest {

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, ho1*6",
    "holdings.fullCallNumber=={value}, GEN*c.1",
    "holdings.normalizedCallNumbers==\"{value}\", GEN",
    "holdings.normalizedCallNumbers==\"{value}\", GEN:",
    "holdings.normalizedCallNumbers==\"{value}\", 332.2",
    "holdings.normalizedCallNumbers==\"{value}\", 332:2",
    "holdingsNormalizedCallNumbers==\"{value}\", GEN",
    "holdingsNormalizedCallNumbers==\"{value}\", GEN:",
    "holdingsNormalizedCallNumbers==\"{value}\", 332.2",
    "holdingsNormalizedCallNumbers==\"{value}\", 332:2",
    "holdingsNormalizedCallNumbers==\"{value}\", 332.2 c.1",
    "holdingsNormalizedCallNumbers==\"{value}\", GEN332.2"
  })
  void canSearchByHoldings_exactMatchWithWildcard(String query, String value) throws Exception {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, hold000000000009",
    "holdings.normalizedCallNumbers==\"{value}\", TK5105.88815 . A58 2004 FT MEADE",
    "holdings.normalizedCallNumbers==\"{value}\", TK510588815",
    "holdings.normalizedCallNumbers==\"{value}\", TK5105.8881:5 . a58",
    "holdingsFullCallNumbers==\"{value}\", suppl. TK5105.88815 . A58 2004 FT MEADE 3",
    "holdingsNormalizedCallNumbers==\"{value}\", TK5105.88815 . A58 2004 FT MEADE",
    "holdingsNormalizedCallNumbers==\"{value}\", TK510588815",
    "holdingsNormalizedCallNumbers==\"{value}\", TK5105.8881:5 . a58",
    "holdingsNormalizedCallNumbers==\"{value}\", TK5105.88815",
    "holdingsNormalizedCallNumbers==\"{value}\", tk510588815 .    A58",
    "holdingsNormalizedCallNumbers==\"{value}\", TK5105",
    "holdingsNormalizedCallNumbers==\"{value}\", tk510588815a582004ftmeade"
  })
  void canSearchByHoldings_exactMatch(String query, String value) throws Exception {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.normalizedCallNumbers==\"{value}\", call suffix",
    "holdings.normalizedCallNumbers==\"{value}\", CAL/number suffix",
    "holdings.normalizedCallNumbers==\"{value}\", prefix number",
    "holdingsNormalizedCallNumbers==\"{value}\", fix",
    "holdingsNormalizedCallNumbers==\"{value}\", number",
    "holdingsNormalizedCallNumbers==\"{value}\", c number",
    "holdingsNormalizedCallNumbers==\"{value}\", call suffix",
    "holdingsNormalizedCallNumbers==\"{value}\", CAL/number suffix",
    "holdingsNormalizedCallNumbers==\"{value}\", prefix number"
  })
  void canSearchByHoldings_negative(String query, String value) throws Exception {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(0)));
  }
}
