package org.folio.api.search;

import static org.folio.support.base.ApiEndpoints.holdingIdsPath;
import static org.folio.support.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.folio.support.sample.SampleInstances.getSemanticWebMatchers;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.Instance;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchHoldingsIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebMatchers(), getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, ho*7",
    "holdings.fullCallNumber=={value}, prefix*suffix",
    "holdings.normalizedCallNumbers==\"{value}\", prefix",
    "holdings.normalizedCallNumbers==\"{value}\", prefix:",
    "holdings.normalizedCallNumbers==\"{value}\", callnumber",
    "holdings.normalizedCallNumbers==\"{value}\", call:number",
    "holdingsNormalizedCallNumbers==\"{value}\", prefix",
    "holdingsNormalizedCallNumbers==\"{value}\", prefix:",
    "holdingsNormalizedCallNumbers==\"{value}\", callnumber",
    "holdingsNormalizedCallNumbers==\"{value}\", call:number",
    "holdingsNormalizedCallNumbers==\"{value}\", callnumbers",
    "holdingsNormalizedCallNumbers==\"{value}\", callnumber suffix",
    "holdingsNormalizedCallNumbers==\"{value}\", CALL.number suffix",
    "holdingsNormalizedCallNumbers==\"{value}\", prefixcallnumber"
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
    "holdingsFullCallNumbers==\"{value}\", TK5105.88815 . A58 2004 FT MEADE",
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

  @Test
  void streamHoldingIds() throws Exception {
    doGet(holdingIdsPath("id=*"))
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("ids[*].id", containsInAnyOrder(
        is("a663dea9-6547-4b2d-9daa-76cadd662272"),
        is("e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19"),
        is("9550c935-401a-4a85-875e-4d1fe7678870"))));
  }
}
