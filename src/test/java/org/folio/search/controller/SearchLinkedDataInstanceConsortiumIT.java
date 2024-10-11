package org.folio.search.controller;

import static org.folio.search.sample.SampleLinkedData.getInstanceSampleAsMap;
import static org.folio.search.utils.LinkedDataTestUtils.toRootContent;
import static org.folio.search.utils.LinkedDataTestUtils.toTitleValue;
import static org.folio.search.utils.LinkedDataTestUtils.toTotalRecords;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SearchLinkedDataInstanceConsortiumIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(LinkedDataInstance.class, CENTRAL_TENANT_ID, getInstanceSampleAsMap());
  }

  @Test
  void searchByLinkedDataInstanceInCentralTenant_shouldReturnCentralTenantRecord() throws Throwable {
    doSearchByLinkedDataInstance(CENTRAL_TENANT_ID, "title all \"titleAbc\"")
      .andExpect(jsonPath(toTotalRecords(), is(1)))
      .andExpect(jsonPath(toTitleValue(toRootContent(0), 0), is("titleAbc def")));
  }

}
