package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.support.base.ApiEndpoints.holdingIds;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SearchHoldingsIT extends BaseIntegrationTest {

  @BeforeAll
  static void createTenant(@Autowired MockMvc mockMvc) {
    setUpTenant(TENANT_ID, mockMvc, getSemanticWebAsMap());
  }

  @AfterAll
  static void removeTenant(@Autowired MockMvc mockMvc) {
    removeTenant(mockMvc, TENANT_ID);
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, hold000000000009",
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
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdings.hrid=={value}, ho*7",
    "holdings.fullCallNumber=={value}, prefix*suffix",
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
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @ParameterizedTest(name = "[{index}] {0}: {1}")
  @CsvSource({
    "holdingsNormalizedCallNumbers==\"{value}\", fix",
    "holdingsNormalizedCallNumbers==\"{value}\", number",
    "holdingsNormalizedCallNumbers==\"{value}\", c number",
    "holdingsNormalizedCallNumbers==\"{value}\", call suffix",
    "holdingsNormalizedCallNumbers==\"{value}\", CAL/number suffix",
    "holdingsNormalizedCallNumbers==\"{value}\", prefix number"
  })
  void canSearchByHoldings_negative(String query, String value) throws Exception {
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(0)));
  }

  @Test
  void streamHoldingIds() throws Exception {
    mockMvc.perform(get(holdingIds("id=*")).headers(defaultHeaders()))
      .andExpect(status().isOk())
      .andExpect(jsonPath("totalRecords", is(3)))
      .andExpect(jsonPath("ids[*].id", is(List.of(
        "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19",
        "9550c935-401a-4a85-875e-4d1fe7678870",
        "a663dea9-6547-4b2d-9daa-76cadd662272"))));
  }
}
