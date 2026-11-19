package org.folio.api;

import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.model.types.ResourceType.CAMPUS;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.search.model.types.ResourceType.INSTITUTION;
import static org.folio.search.model.types.ResourceType.LIBRARY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_AUTHORITY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.model.types.ResourceType.LOCATION;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.consortiumBatchItemsSearchPath;
import static org.folio.support.base.ApiEndpoints.consortiumHoldingsSearchPath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.sample.SampleInstances.getSemanticWebId;
import static org.folio.support.sample.SampleInstances.getSemanticWebMatchers;
import static org.folio.support.testdata.ConsortiumTestDataManager.CALL_NUMBER_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.CALL_NUMBER_LC_TYPE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.CLASSIFICATION_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.CONTRIBUTOR_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.SUBJECT_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.holdingsCount;
import static org.folio.support.testdata.ConsortiumTestDataManager.instancesCount;
import static org.folio.support.testdata.ConsortiumTestDataManager.itemIds;
import static org.folio.support.testdata.ConsortiumTestDataManager.loadInventory;
import static org.folio.support.testdata.SharedTestDataManager.campusesCount;
import static org.folio.support.testdata.SharedTestDataManager.institutionsCount;
import static org.folio.support.testdata.SharedTestDataManager.librariesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataAuthoritiesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataWorksCount;
import static org.folio.support.testdata.SharedTestDataManager.loadCampuses;
import static org.folio.support.testdata.SharedTestDataManager.loadInstitutions;
import static org.folio.support.testdata.SharedTestDataManager.loadLibraries;
import static org.folio.support.testdata.SharedTestDataManager.loadLinkedDataAuthorities;
import static org.folio.support.testdata.SharedTestDataManager.loadLinkedDataWorks;
import static org.folio.support.testdata.SharedTestDataManager.loadLocations;
import static org.folio.support.testdata.SharedTestDataManager.locationsCount;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.api.browse.BrowseCallNumberConsortiumIT;
import org.folio.api.browse.BrowseClassificationConsortiumIT;
import org.folio.api.browse.BrowseContributorConsortiumIT;
import org.folio.api.browse.BrowseSubjectConsortiumIT;
import org.folio.api.consortiumsearch.ConsortiumSearchCampusesIT;
import org.folio.api.consortiumsearch.ConsortiumSearchHoldingsIT;
import org.folio.api.consortiumsearch.ConsortiumSearchInstitutionsIT;
import org.folio.api.consortiumsearch.ConsortiumSearchItemsIT;
import org.folio.api.consortiumsearch.ConsortiumSearchLibrariesIT;
import org.folio.api.consortiumsearch.ConsortiumSearchLocationsIT;
import org.folio.api.search.SearchInstanceConsortiumIT;
import org.folio.api.search.SearchLinkedDataWorkConsortiumIT;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.scheduled.ScheduledInstanceSubResourcesService;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.folio.support.base.SubResourceLockManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Shared integration-test suite for consortium search and browse.
 *
 * <p>All test data is loaded once in {@link #setUp} via
 * {@link org.folio.support.testdata.SharedTestDataManager} and
 * {@link org.folio.support.testdata.ConsortiumTestDataManager} and shared across every nested test
 * class for the lifetime of the suite. Records are indexed into central, member and member2
 * tenants to exercise consortium-specific behaviour.
 *
 * <p>Test data is maintained in
 * {@code src/test/resources/test-data/} — see
 * {@code src/test/resources/test-data/manage/README.md} for the management guide.
 */
@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.instance-children-index-enabled=true"
})
public class SearchBrowseConsortiumSuiteIT extends BaseIntegrationTest {

  @BeforeAll
  static void setUp(@Autowired SubResourcesLockRepository lockRepository,
                    @Autowired ScheduledInstanceSubResourcesService scheduledSubResourcesService) {
    setUpCentralTenant();
    setUpMemberTenant();
    enableTenant(MEMBER2_TENANT_ID);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CALL_NUMBERS);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CLASSIFICATIONS);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CONTRIBUTORS);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_SUBJECTS);
    setUpInventory(lockRepository, scheduledSubResourcesService);
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(CENTRAL_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(MEMBER2_TENANT_ID);
  }

  private static void setUpCentralTenant() {
    enableTenant(CENTRAL_TENANT_ID);
    loadLinkedDataAuthorities(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLinkedDataWorks(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLocations(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadLibraries(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadInstitutions(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    loadCampuses(CENTRAL_TENANT_ID, BaseSharedTest::indexRecords);
    awaitIndexedResourceCounts(LINKED_DATA_WORK, CENTRAL_TENANT_ID, linkedDataWorksCount());
    awaitIndexedResourceCounts(LINKED_DATA_AUTHORITY, CENTRAL_TENANT_ID, linkedDataAuthoritiesCount());
    awaitIndexedResourceCounts(LOCATION, CENTRAL_TENANT_ID, locationsCount());
    awaitIndexedResourceCounts(LIBRARY, CENTRAL_TENANT_ID, librariesCount());
    awaitIndexedResourceCounts(INSTITUTION, CENTRAL_TENANT_ID, institutionsCount());
    awaitIndexedResourceCounts(CAMPUS, CENTRAL_TENANT_ID, campusesCount());
  }

  private static void setUpMemberTenant() {
    enableTenant(MEMBER_TENANT_ID);
    loadLocations(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    loadLibraries(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    loadInstitutions(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    loadCampuses(MEMBER_TENANT_ID, BaseSharedTest::indexRecords);
    // Load same location records in member tenant - creates separate documents in central index
    awaitIndexedResourceCounts(LOCATION, CENTRAL_TENANT_ID, locationsCount() * 2);
    awaitIndexedResourceCounts(LIBRARY, CENTRAL_TENANT_ID, librariesCount() * 2);
    awaitIndexedResourceCounts(INSTITUTION, CENTRAL_TENANT_ID, institutionsCount() * 2);
    awaitIndexedResourceCounts(CAMPUS, CENTRAL_TENANT_ID, campusesCount() * 2);
  }

  /**
   * Creates the shared ECS inventory data set: shared instances in the central tenant, local instances in the
   * member and member2 tenants, with holdings, items, call numbers, contributors, classifications and subjects.
   * Sub-resources are locked so that their scheduled processing does not race with the data load.
   */
  private static void setUpInventory(SubResourcesLockRepository lockRepository,
                                     ScheduledInstanceSubResourcesService scheduledSubResourcesService) {
    var lockManager = new SubResourceLockManager(lockRepository, CENTRAL_TENANT_ID, ReindexEntityType.CALL_NUMBER,
      ReindexEntityType.CONTRIBUTOR, ReindexEntityType.CLASSIFICATION, ReindexEntityType.SUBJECT,
      ReindexEntityType.ITEM, ReindexEntityType.INSTANCE);
    loadInventory(lockManager, inventoryApi,
      SearchBrowseConsortiumSuiteIT::awaitInstancesVisible, scheduledSubResourcesService::persistChildren);
    awaitSemanticWebInstance();
    awaitIndexedResourceCounts(INSTANCE, CENTRAL_TENANT_ID, instancesCount());
    awaitHoldingsAndItemsIndexed();
    awaitIndexedResourceCounts(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID, CALL_NUMBER_COUNT);
    awaitIndexedResourceCounts(INSTANCE_CONTRIBUTOR, CENTRAL_TENANT_ID, CONTRIBUTOR_COUNT);
    awaitIndexedResourceCounts(INSTANCE_CLASSIFICATION, CENTRAL_TENANT_ID, CLASSIFICATION_COUNT);
    awaitIndexedResourceCounts(INSTANCE_SUBJECT, CENTRAL_TENANT_ID, SUBJECT_COUNT);
    updateCnConfig(List.of(UUID.fromString(CALL_NUMBER_LC_TYPE_ID)), BrowseOptionType.LC, ShelvingOrderAlgorithmType.LC,
      CENTRAL_TENANT_ID);
  }

  private static void awaitInstancesVisible(String tenantId, List<String> instanceIds) {
    assertSearchByIdsCount(instanceSearchPath(), instanceIds, instanceIds.size(), tenantId);
  }

  private static void awaitSemanticWebInstance() {
    awaitAssertion(() -> doSearch(instanceSearchPath(), MEMBER_TENANT_ID,
      Map.of(QUERY_PARAM, "id==" + getSemanticWebId(), EXPAND_ALL_PARAM, "true"))
      .andExpectAll(getSemanticWebMatchers().toArray(new ResultMatcher[0])));
  }

  /**
   * Holdings and items are sent through their own Kafka topics, so the instance documents can be searchable before
   * their holdings and items are added to them; the consortium holdings/items endpoints read them from there.
   */
  private static void awaitHoldingsAndItemsIndexed() {
    awaitAssertion(() -> doGet(consortiumHoldingsSearchPath(), CENTRAL_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(holdingsCount()))));
    var itemIds = itemIds();
    var itemsRequest = new BatchIdsDto().identifierType(BatchIdsDto.IdentifierTypeEnum.ID).identifierValues(itemIds);
    awaitAssertion(() -> doPost(consortiumBatchItemsSearchPath(), CENTRAL_TENANT_ID, itemsRequest)
      .andExpect(jsonPath("$.totalRecords", is(itemIds.size()))));
  }

  @Nested
  class BrowseCallNumber extends BrowseCallNumberConsortiumIT { }

  @Nested
  class BrowseClassification extends BrowseClassificationConsortiumIT { }

  @Nested
  class BrowseContributor extends BrowseContributorConsortiumIT { }

  @Nested
  class BrowseSubject extends BrowseSubjectConsortiumIT { }

  @Nested
  class ConsortiumSearchHoldings extends ConsortiumSearchHoldingsIT { }

  @Nested
  class ConsortiumSearchItems extends ConsortiumSearchItemsIT { }

  @Disabled
  @Nested
  class SearchInstance extends SearchInstanceConsortiumIT { }

  @Nested
  class SearchLinkedDataWork extends SearchLinkedDataWorkConsortiumIT { }

  @Nested
  class ConsortiumSearchLocations extends ConsortiumSearchLocationsIT { }

  @Nested
  class ConsortiumSearchLibraries extends ConsortiumSearchLibrariesIT { }

  @Nested
  class ConsortiumSearchInstitutions extends ConsortiumSearchInstitutionsIT { }

  @Nested
  class ConsortiumSearchCampuses extends ConsortiumSearchCampusesIT { }
}
