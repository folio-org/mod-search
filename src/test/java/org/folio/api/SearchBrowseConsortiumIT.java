package org.folio.api;

import static org.folio.search.model.types.ResourceType.CAMPUS;
import static org.folio.search.model.types.ResourceType.INSTITUTION;
import static org.folio.search.model.types.ResourceType.LIBRARY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_INSTANCE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.model.types.ResourceType.LOCATION;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.testdata.SharedTestDataManager.campusesCount;
import static org.folio.support.testdata.SharedTestDataManager.institutionsCount;
import static org.folio.support.testdata.SharedTestDataManager.librariesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataInstancesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataWorksCount;
import static org.folio.support.testdata.SharedTestDataManager.loadCampuses;
import static org.folio.support.testdata.SharedTestDataManager.loadInstitutions;
import static org.folio.support.testdata.SharedTestDataManager.loadLibraries;
import static org.folio.support.testdata.SharedTestDataManager.loadLinkedDataInstances;
import static org.folio.support.testdata.SharedTestDataManager.loadLinkedDataWorks;
import static org.folio.support.testdata.SharedTestDataManager.loadLocations;
import static org.folio.support.testdata.SharedTestDataManager.locationsCount;

import org.folio.api.consortiumsearch.ConsortiumSearchCampusesIT;
import org.folio.api.consortiumsearch.ConsortiumSearchInstitutionsIT;
import org.folio.api.consortiumsearch.ConsortiumSearchLibrariesIT;
import org.folio.api.consortiumsearch.ConsortiumSearchLocationsIT;
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
    setUpCentralTenant();
    setUpMemberTenant();
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(CENTRAL_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
  }

  private static void setUpCentralTenant() {
    enableTenant(CENTRAL_TENANT_ID);
    loadLinkedDataInstances(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLinkedDataWorks(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLocations(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLibraries(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadInstitutions(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadCampuses(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    verifyIndexedResourceCounts(LINKED_DATA_WORK, CENTRAL_TENANT_ID, linkedDataWorksCount());
    verifyIndexedResourceCounts(LINKED_DATA_INSTANCE, CENTRAL_TENANT_ID, linkedDataInstancesCount());
    verifyIndexedResourceCounts(LOCATION, CENTRAL_TENANT_ID, locationsCount());
    verifyIndexedResourceCounts(LIBRARY, CENTRAL_TENANT_ID, librariesCount());
    verifyIndexedResourceCounts(INSTITUTION, CENTRAL_TENANT_ID, institutionsCount());
    verifyIndexedResourceCounts(CAMPUS, CENTRAL_TENANT_ID, campusesCount());
  }

  private static void setUpMemberTenant() {
    enableTenant(MEMBER_TENANT_ID);
    loadLocations(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    loadLibraries(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    loadInstitutions(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    loadCampuses(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    // Load same location records in member tenant - creates separate documents in central index
    verifyIndexedResourceCounts(LOCATION, CENTRAL_TENANT_ID, locationsCount() * 2);
    verifyIndexedResourceCounts(LIBRARY, CENTRAL_TENANT_ID, librariesCount() * 2);
    verifyIndexedResourceCounts(INSTITUTION, CENTRAL_TENANT_ID, institutionsCount() * 2);
    verifyIndexedResourceCounts(CAMPUS, CENTRAL_TENANT_ID, campusesCount() * 2);
  }

  @Nested
  class SearchLinkedDataWork extends SearchLinkedDataWorkConsortiumIT { }

  @Nested
  class SearchLinkedDataInstance extends SearchLinkedDataInstanceConsortiumIT { }

  @Nested
  class ConsortiumSearchLocations extends ConsortiumSearchLocationsIT { }

  @Nested
  class ConsortiumSearchLibraries extends ConsortiumSearchLibrariesIT { }

  @Nested
  class ConsortiumSearchInstitutions extends ConsortiumSearchInstitutionsIT { }

  @Nested
  class ConsortiumSearchCampuses extends ConsortiumSearchCampusesIT { }
}
