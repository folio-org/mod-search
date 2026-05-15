package org.folio.api.search;

import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.utils.LinkedDataTestUtils.toRootContent;
import static org.folio.support.utils.LinkedDataTestUtils.toTitleValue;
import static org.folio.support.utils.LinkedDataTestUtils.toTotalRecords;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class SearchLinkedDataWorkConsortiumIT extends BaseSharedTest {

  @Test
  void searchByLinkedDataWorkInCentralTenant_shouldReturnCentralTenantRecord() throws Throwable {
    doSearchByLinkedDataWork(CENTRAL_TENANT_ID, "title all \"titleAbc\" sortBy title")
      .andExpect(jsonPath(toTotalRecords(), is(2)))
      .andExpect(jsonPath(toTitleValue(toRootContent(0), 0), is("titleAbc def")));
  }
}
