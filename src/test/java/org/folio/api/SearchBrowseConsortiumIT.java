package org.folio.api;

import static org.folio.search.model.types.ResourceType.LINKED_DATA_INSTANCE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataInstancesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataWorksCount;
import static org.folio.support.testdata.SharedTestDataManager.loadLinkedDataInstances;
import static org.folio.support.testdata.SharedTestDataManager.loadLinkedDataWorks;

import org.folio.api.search.SearchLinkedDataInstanceConsortiumIT;
import org.folio.api.search.SearchLinkedDataWorkConsortiumIT;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;

@IntegrationTest
public class SearchBrowseConsortiumIT extends BaseIntegrationTest {

  @BeforeAll
  static void setUp() {
    enableTenant(CENTRAL_TENANT_ID);
    loadLinkedDataInstances(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLinkedDataWorks(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    verifyIndexedResourceCounts(LINKED_DATA_WORK, CENTRAL_TENANT_ID, linkedDataWorksCount());
    verifyIndexedResourceCounts(LINKED_DATA_INSTANCE, CENTRAL_TENANT_ID, linkedDataInstancesCount());
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Nested
  class SearchLinkedDataWork extends SearchLinkedDataWorkConsortiumIT { }

  @Nested
  class SearchLinkedDataInstance extends SearchLinkedDataInstanceConsortiumIT { }
}
