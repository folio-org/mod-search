package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchItemIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @CsvSource({
    "items.fullCallNumber=={value}, prefix-90000 TK51*",
    "items.effectiveCallNumberComponents=={value}, *suffix-10101",
    "items.normalizedCallNumbers=={value}, prefix-90000",
    "items.normalizedCallNumbers=={value}, prefix90000",
    "items.normalizedCallNumbers=={value}, prefix.9",
    "items.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "items.normalizedCallNumbers=={value}, TK5105.88815",
    "item.fullCallNumber=={value}, prefix-90000 TK51*",
    "item.effectiveCallNumberComponents=={value}, *suffix-10101",
    "item.normalizedCallNumbers=={value}, prefix-90000",
    "item.normalizedCallNumbers=={value}, prefix90000",
    "item.normalizedCallNumbers=={value}, prefix.9",
    "item.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "item.normalizedCallNumbers=={value}, TK5105.88815",
    "itemNormalizedCallNumbers=={value}, prefix-90000",
    "itemNormalizedCallNumbers=={value}, prefix90000",
    "itemNormalizedCallNumbers=={value}, prefix.9",
    "itemNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE",
    "itemNormalizedCallNumbers=={value}, TK5105.88815",
    "itemNormalizedCallNumbers=={value}, prefix90000 TK510588815",
    "itemNormalizedCallNumbers=={value}, tk510588815",
    "itemNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE suffix-90000",
    "itemNormalizedCallNumbers=={value}, TK510588815A582004FT MEADE suffix90000"
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_wildcardMatch(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @CsvSource({
    "items.normalizedCallNumbers=={value}, 88815 suffix90000",
    "items.normalizedCallNumbers=={value}, prefix TK510588815",
    "items.normalizedCallNumbers=={value}, 510588815",
    "items.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
    "item.normalizedCallNumbers=={value}, 88815 suffix90000",
    "item.normalizedCallNumbers=={value}, prefix TK510588815",
    "item.normalizedCallNumbers=={value}, 510588815",
    "item.normalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
    "itemNormalizedCallNumbers=={value}, fix-90000",
    "itemNormalizedCallNumbers=={value}, 90000",
    "itemNormalizedCallNumbers=={value}, 88815.A58 2004 FT MEADE",
    "itemNormalizedCallNumbers=={value}, 88815 suffix90000",
    "itemNormalizedCallNumbers=={value}, prefix TK510588815",
    "itemNormalizedCallNumbers=={value}, 510588815",
    "itemNormalizedCallNumbers=={value}, TK5105.88815.A58 2004 FT MEADE 90000",
  })
  @ParameterizedTest(name = "[{index}] {0}: {1}")
  void canSearchByItems_negative(String query, String value) throws Throwable {
    doSearchByInstances(prepareQuery(query, value))
      .andExpect(jsonPath("totalRecords", is(0)));
  }
}
