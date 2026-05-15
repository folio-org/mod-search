package org.folio.api.search;

import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.utils.LinkedDataTestUtils.toRootContent;
import static org.folio.support.utils.LinkedDataTestUtils.toTitleValue;
import static org.folio.support.utils.LinkedDataTestUtils.toTotalRecords;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class SearchLinkedDataInstanceConsortiumIT extends BaseSharedTest {

  @Test
  void searchByLinkedDataInstanceInCentralTenant_shouldReturnCentralTenantRecord() throws Throwable {
    doSearchByLinkedDataInstance(CENTRAL_TENANT_ID, "title all \"titleAbc\" sortBy title")
      .andExpect(jsonPath(toTotalRecords(), is(2)))
      .andExpect(jsonPath(toTitleValue(toRootContent(0), 0), is("titleAbc def")));
  }
}
