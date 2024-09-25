package org.folio.search.controller;

import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.sample.SampleInstances.getSemanticWebId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.Instance;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@IntegrationTest
class SearchByAllFieldsIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, () -> enableFeature(SEARCH_ALL_FIELDS), getSemanticWebAsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @ValueSource(strings = {
    // instance field values
    "5bf370e0-8cca-4d9c-82e4-5170ab2a0a39",
    "A sem\\\\ntic web primer",
    "An alternative title",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Cambridge, Mass.",
    "Ant\\\\niou, Grigoris matthew",
    "Ant\\\\niou matthew",
    "HD1691 .I5 1967",
    "Cambridge, Mass.",
    "MIT Press",
    "c2004",
    "1\\\\9u",
    "*\\\\9u",
    "1*9u",
    "1\\\\9*",
    "2022",
    "202*",
    "2*22",
    "*22",

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
    doSearchByInstances(prepareQuery("cql.all=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }

  @ValueSource(strings = {
    "5bf370e0-8cca-4d9c-82e4-5170ab2a0a39",
    "A sem\\\\ntic web primer",
    "An alternative title",
    "Cooperative information systems",
    "0262012103",
    "2003065165",
    "Ant\\\\niou, Grigoris matthew",
    "HD1691 .I5 1967",
    "Cambridge, Mass.",
    "2020-12-08T15:47:13.625+00:00",
    "2020-12-08T15:47:13.625+0000",
    "MIT Press",
    "c2004",
    "1\\\\9u",
    "*\\\\9u",
    "1*9u",
    "1\\\\9*",
    "2022",
    "202*",
    "2*22",
    "*22"
  })
  @ParameterizedTest(name = "[{index}] cql.allInstances='{query}', query=''{0}''")
  void canSearchByInstanceFieldValues_positive(String cqlQuery) throws Throwable {
    doSearchByInstances(prepareQuery("cql.allInstances=\"{value}\"", cqlQuery), 1, 0)
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
    doSearchByInstances(prepareQuery("cql.allHoldings=\"{value}\"", cqlQuery), 1, 0)
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
    doSearchByInstances(prepareQuery("cql.allItems=\"{value}\"", cqlQuery), 1, 0)
      .andExpect(jsonPath("totalRecords", is(1)))
      .andExpect(jsonPath("instances[0].id", is(getSemanticWebId())));
  }
}
