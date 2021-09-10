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
class SearchItemIT extends BaseIntegrationTest {

  @CsvSource({
    "items.fullCallNumber=={value}, prefix-90000 TK51*",
    "items.effectiveCallNumberComponents=={value}, *suffix-10101",
    "itemsNormalizedCallNumbers=={value}, prefix-90000",
    "itemsNormalizedCallNumbers=={value}, prefix90000",
    "itemsNormalizedCallNumbers=={value}, prefix.9",
    "itemsNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "itemsNormalizedCallNumbers=={value}, TK5105.88815",
    "itemsNormalizedCallNumbers=={value}, prefix90000 TK510588815",
    "itemsNormalizedCallNumbers=={value}, tk510588815",
    "itemsNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE suffix-90000",
    "itemsNormalizedCallNumbers=={value}, TK510588815A582004FT MEADE suffix90000",
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_wildcardMatch(String query, String value) throws Throwable {
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @CsvSource({
    "itemsNormalizedCallNumbers=={value}, fix-90000",
    "itemsNormalizedCallNumbers=={value}, 90000",
    "itemsNormalizedCallNumbers=={value}, 88815.A58 2004 FT MEADE",
    "itemsNormalizedCallNumbers=={value}, 88815 suffix90000",
    "itemsNormalizedCallNumbers=={value}, prefix TK510588815",
    "itemsNormalizedCallNumbers=={value}, 510588815",
    "itemsNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_negative(String query, String value) throws Throwable {
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(0)));
  }
}
