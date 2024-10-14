package org.folio.search.controller;

import static org.folio.search.sample.SampleLinkedData.getHubSample2AsMap;
import static org.folio.search.sample.SampleLinkedData.getHubSampleAsMap;
import static org.folio.search.utils.LinkedDataTestUtils.toTotalRecords;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.LinkedDataHub;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchLinkedDataHubIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(LinkedDataHub.class, getHubSampleAsMap(), getHubSample2AsMap());
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @DisplayName("search by linked data hub")
  @ParameterizedTest(name = "[{0}] {2}")
  @CsvSource(value = {
    " 1, 2, id any \"*\"",
    " 2, 1, id = \"123\"",
    " 3, 1, id = \"456\"",
    " 4, 2, originalId = \"*\"",
    " 5, 1, originalId = \"995ff81e-0ab6-4124-9e45-d6fa73c78d66\"",
    " 6, 1, originalId = \"1d6f83d6-841b-4e01-8e03-1559a683b567\"",
    " 7, 2, hubAAP = \"*\"",
    " 8, 1, hubAAP = \"*ABC\"",
    " 9, 1, hubAAP = \"*XYZ\"",
    "10, 2, title = \"*\"",
    "11, 1, title = \"HubTitleABC\"",
    "12, 1, title = \"HubTitleXYZ\""
  })
  void searchByLinkedDataHub_parameterized_singleResult(int index, int size, String query) throws Throwable {
    doSearchByLinkedDataHub(query)
      .andExpect(jsonPath(toTotalRecords(), is(size)));
  }
}
