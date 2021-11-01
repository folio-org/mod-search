package org.folio.search.controller;

import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.text.MessageFormat;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
class SearchByAllFieldsIT extends BaseIntegrationTest {

  @BeforeAll
  static void createTenant(@Autowired MockMvc mockMvc) {
    setUpTenant(TENANT_ID, mockMvc, () -> enableFeature(TENANT_ID, SEARCH_ALL_FIELDS, mockMvc), getSemanticWebAsMap());
  }

  @AfterAll
  static void removeTenant(@Autowired MockMvc mockMvc) {
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
    "Cambridge, Mass.",

    // holding field values
    "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19",
    "ho00000000006",
    "ho00000000007",
    "TK5105.88815 . A58 2004 FT MEADE",
    "TK5105.88815",
    "Includes bibliographical references and index of holdings.",

    // item field values
    "7212ba6a-8dcf-45a1-be9a-ffaa847c4423",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number",
    "Available",
    "Copy 2",
    "207b9372-127f-4bdd-83e0-147c9fe9bc16"
  })
  @ParameterizedTest(name = "[{index}] cql.all='{query}', query=''{0}''")
  void canSearchByAllFieldValues_positive(String cqlQuery) throws Throwable {
    mockMvc.perform(searchRequest("cql.all=\"{0}\"", cqlQuery))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
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
    "Cambridge, Mass.",
    "2020-12-08T15:47:13.625+00:00",
    "2020-12-08T15:47:13.625+0000"
  })
  @ParameterizedTest(name = "[{index}] cql.allInstances='{query}', query=''{0}''")
  void canSearchByInstanceFieldValues_positive(String cqlQuery) throws Throwable {
    mockMvc.perform(searchRequest("cql.allInstances=\"{0}\"", cqlQuery))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "e3ff6133-b9a2-4d4c-a1c9-dc1867d4df19",
    "ho00000000006",
    "ho00000000007",
    "TK5105.88815 . A58 2004 FT MEADE",
    "TK5105.88815",
    "Includes bibliographical references and index of holdings.",
  })
  @ParameterizedTest(name = "[{index}] cql.allHoldings='{query}', query=''{0}''")
  void canSearchByHoldingFieldValues_positive(String cqlQuery) throws Throwable {
    mockMvc.perform(searchRequest("cql.allHoldings=\"{0}\"", cqlQuery))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "7212ba6a-8dcf-45a1-be9a-ffaa847c4423",
    "TK5105.88815 . A58 2004 FT MEADE",
    "item000000000014",
    "item_accession_number",
    "Available",
    "Copy 2",
    "207b9372-127f-4bdd-83e0-147c9fe9bc16"
  })
  @ParameterizedTest(name = "[{index}] cql.allItems='{query}', query=''{0}''")
  void canSearchByItemFieldValues_positive(String cqlQuery) throws Throwable {
    mockMvc.perform(searchRequest("cql.allItems=\"{0}\"", cqlQuery))
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  private static MockHttpServletRequestBuilder searchRequest(String template, String cqlQuery) {
    return get(instanceSearchPath())
      .param("query", MessageFormat.format(template, cqlQuery))
      .param("limit", "1")
      .headers(defaultHeaders());
  }
}
