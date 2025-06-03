package org.folio.api.search;

import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.sample.SampleLinkedData.getWorkSampleAsMap;
import static org.folio.support.utils.LinkedDataTestUtils.toRootContent;
import static org.folio.support.utils.LinkedDataTestUtils.toTitleValue;
import static org.folio.support.utils.LinkedDataTestUtils.toTotalRecords;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class SearchLinkedDataWorkConsortiumIT extends BaseConsortiumIntegrationTest {

  @BeforeAll
  static void prepare() {
    setUpTenant(LinkedDataWork.class, CENTRAL_TENANT_ID, getWorkSampleAsMap());
  }

  @Test
  void searchByLinkedDataWorkInCentralTenant_shouldReturnCentralTenantRecord() throws Throwable {
    doSearchByLinkedDataWork(CENTRAL_TENANT_ID, "title all \"titleAbc\"")
      .andExpect(jsonPath(toTotalRecords(), is(1)))
      .andExpect(jsonPath(toTitleValue(toRootContent(0), 0), is("titleAbc def")));
  }
}
