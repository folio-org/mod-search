package org.folio.search.controller;

import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@TestPropertySource(properties = {
  "application.search-config.disabled-search-options.instance="
})
public class SearchByAllFieldsIT extends BaseIntegrationTest {

  private static final String TENANT_ID = "search_all";

  @BeforeAll
  static void beforeAll(@Autowired MockMvc mockMvc) {
    setUpTenant(TENANT_ID, mockMvc, getSemanticWeb());
  }

  @AfterAll
  static void afterAll(@Autowired MockMvc mockMvc) {
    removeTenant(mockMvc, TENANT_ID);
  }

  @ValueSource(strings = {
    // instance field values
    "5bf370e0-8cca-4d9c-82e4-5170ab2a0a39",
    "A semantic web primer",
    "An alternative title",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Antoniou, Grigoris",
    "TK5105.88815 .A58 2004",

    // holding field values
    "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19",
    "ho00000000006",
    "ho00000000007",
    "TK5105.88815 . A58 2004 FT MEADE",
    "Includes bibliographical references and index of holdings.",

    // item field values
    "7212ba6a-8dcf-45a1-be9a-ffaa847c4423",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number",
    "Available",
  })
  @ParameterizedTest(name = "[{index}] cql.all='{query}', query=''{0}''")
  void canSearchByAllFieldValues_positive(String value) throws Throwable {
    var query = "cql.all=\"{query}\"";
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @ValueSource(strings = {
    "5bf370e0-8cca-4d9c-82e4-5170ab2a0a39",
    "A semantic web primer",
    "An alternative title",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Antoniou, Grigoris",
    "TK5105.88815 .A58 2004",
  })
  @ParameterizedTest(name = "[{index}] cql.allInstance='{query}', query=''{0}''")
  void canSearchByInstanceFieldValues_positive(String value) throws Throwable {
    var query = "cql.allInstance=\"{query}\"";
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @ValueSource(strings = {
    "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19",
    "ho00000000006",
    "ho00000000007",
    "TK5105.88815 . A58 2004 FT MEADE",
    "Includes bibliographical references and index of holdings.",
  })
  @ParameterizedTest(name = "[{index}] cql.allHoldings='{query}', query=''{0}''")
  void canSearchByHoldingFieldValues_positive(String value) throws Throwable {
    var query = "cql.allHoldings=\"{query}\"";
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }

  @ValueSource(strings = {
    "7212ba6a-8dcf-45a1-be9a-ffaa847c4423",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number",
    "Available",
  })
  @ParameterizedTest(name = "[{index}] cql.allItems='{query}', query=''{0}''")
  void canSearchByItemFieldValues_positive(String value) throws Throwable {
    var query = "cql.allItems=\"{query}\"";
    doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWeb().getId())));
  }
}
