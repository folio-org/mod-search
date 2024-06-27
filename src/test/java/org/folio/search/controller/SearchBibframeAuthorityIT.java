package org.folio.search.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.BibframeAuthority;
import org.folio.search.sample.SampleBibframe;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@IntegrationTest
class SearchBibframeAuthorityIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(BibframeAuthority.class, 2,
      SampleBibframe.getBibframeAuthorityConceptSampleAsMap(), SampleBibframe.getBibframeAuthorityPersonSampleAsMap()
    );
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @DisplayName("search by authority bibframe (all  authorities are found)")
  @ParameterizedTest(name = "[{0}] {2}")
  @CsvSource({
    " 1, 2, label any \"*\"",
    " 2, 2, label any \"*-*\"",
    " 3, 2, label any \"*-label\"",
    " 4, 2, label <> \"lab\"",
    " 5, 1, label any \"concept-*\"",
    " 6, 1, label = \"concept-label\"",
    " 7, 1, label any \"person-*\"",
    " 8, 1, label = \"person-label\"",
    " 9, 1, type = \"PERSON\"",
    "10, 1, type = \"CONCEPT\"",
    "11, 1, lccn = \"sh9876543210\"",
    "12, 0, lccn = \"s h9876543210\"",
    "13, 1, lccn = \"sh0123456789\"",
    "14, 0, lccn = \"sh 0123456789\"",
    "15, 1, lccn = \"n0123456789\"",
    "16, 0, lccn = \"n 0123456789\"",
    "17, 2, lccn any \"*0123456789\"",
    "18, 1, lccn any \"*0123456789\" AND type = \"PERSON\"",
    "19, 1, lccn any \"*0123456789\" AND type = \"CONCEPT\""
  })
  void searchByBibframe_parameterized_singleResult(int index, int size, String query) throws Throwable {
    doSearchByBibframeAuthority(query)
      .andExpect(jsonPath("$.totalRecords", is(size)));
  }
}
