package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchHoldingsIT extends BaseIntegrationTest {

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, hold000000000009"
  })
  void canSearchByHoldings_exactMatch(String query, String value) throws Exception {
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, ho*7"
  })
  void canSearchByHoldings_exactMatchWithWildcard(String query, String value) throws Exception {
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }
}
