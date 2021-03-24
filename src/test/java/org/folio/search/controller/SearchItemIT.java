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
public class SearchItemIT extends BaseIntegrationTest {

  @CsvSource({
    "items.fullCallNumber=={value}, prefix-90000 TK51*",
    "items.effectiveCallNumberComponents=={value}, *suffix-10101"
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_wildcardMatch(String query, String value) throws Throwable {
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }
}
