package org.folio.api;

import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_AUTHORITY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_HUB;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.testdata.SharedTestDataManager.LockManager;
import static org.folio.support.testdata.SharedTestDataManager.instancesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataAuthoritiesCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataHubsCount;
import static org.folio.support.testdata.SharedTestDataManager.linkedDataWorksCount;
import static org.folio.support.testdata.SharedTestDataManager.loadAll;

import java.sql.Timestamp;
import org.folio.api.browse.BrowseAuthorityIT;
import org.folio.api.browse.BrowseCallNumberIT;
import org.folio.api.browse.BrowseClassificationIT;
import org.folio.api.browse.BrowseContributorIT;
import org.folio.api.browse.BrowseSubjectIT;
import org.folio.api.facet.FacetAuthorityIT;
import org.folio.api.facet.FacetInstanceCallNumberIT;
import org.folio.api.facet.FacetInstanceContributorIT;
import org.folio.api.facet.FacetInstanceIT;
import org.folio.api.facet.FacetInstanceSubjectIT;
import org.folio.api.search.SearchAuthorityIT;
import org.folio.api.search.SearchByEmptyValuesIT;
import org.folio.api.search.SearchHoldingsIT;
import org.folio.api.search.SearchInstanceIT;
import org.folio.api.search.SearchItemIT;
import org.folio.api.search.SearchLinkedDataAuthorityIT;
import org.folio.api.search.SearchLinkedDataHubIT;
import org.folio.api.search.SearchLinkedDataInstanceIT;
import org.folio.api.search.SearchLinkedDataWorkIT;
import org.folio.api.search.SortAuthorityIT;
import org.folio.api.search.SortInstanceIT;
import org.folio.api.search.SortItemIT;
import org.folio.api.searchids.StreamResourceIdsIT;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.scheduled.ScheduledInstanceSubResourcesService;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * Shared integration-test suite for search, browse, facets, sort, and stream-IDs.
 *
 * <p>All test data is loaded once in {@link #setUpSharedTenant} via
 * {@link org.folio.support.testdata.SharedTestDataManager} and shared across every nested test
 * class for the lifetime of the suite.
 *
 * <p>Test data is maintained in
 * {@code src/test/resources/test-data/} — see
 * {@code src/test/resources/test-data/manage/README.md} for the management guide.
 */
@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class SearchBrowseSuiteIT extends BaseIntegrationTest {

  private static final int EXPECTED_AUTHORITY_COUNT = 132;
  private static final int EXPECTED_CALL_NUMBER_COUNT = 116;
  private static final int EXPECTED_CLASSIFICATION_COUNT = 92;
  private static final int EXPECTED_CONTRIBUTOR_COUNT = 68;
  private static final int EXPECTED_SUBJECT_COUNT = 52;

  @BeforeAll
  static void setUpSharedTenant(
    @Autowired SubResourcesLockRepository lockRepo,
    @Autowired ScheduledInstanceSubResourcesService scheduledSubResourcesService) {
    enableTenant(TENANT_ID);
    enableFeature(TENANT_ID, SEARCH_ALL_FIELDS);
    enableFeature(TENANT_ID, BROWSE_CALL_NUMBERS);
    enableFeature(TENANT_ID, BROWSE_CONTRIBUTORS);
    enableFeature(TENANT_ID, BROWSE_SUBJECTS);
    enableFeature(TENANT_ID, BROWSE_CLASSIFICATIONS);
    loadAll(TENANT_ID,
      createLockManager(lockRepo),
      scheduledSubResourcesService::persistChildren,
      BaseSharedTest::indexRecords,
      SearchBrowseSuiteIT::awaitSubResourceIndexing);
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(TENANT_ID);
  }

  private static LockManager createLockManager(SubResourcesLockRepository lockRepo) {
    return new SubResourceLockManager(lockRepo);
  }

  private static void awaitSubResourceIndexing() {
    awaitIndexedResourceCounts(INSTANCE, TENANT_ID, instancesCount());
    awaitIndexedResourceCounts(AUTHORITY, TENANT_ID, EXPECTED_AUTHORITY_COUNT);
    awaitIndexedResourceCounts(INSTANCE_CALL_NUMBER, TENANT_ID, EXPECTED_CALL_NUMBER_COUNT);
    awaitIndexedResourceCounts(INSTANCE_CLASSIFICATION, TENANT_ID, EXPECTED_CLASSIFICATION_COUNT);
    awaitIndexedResourceCounts(INSTANCE_CONTRIBUTOR, TENANT_ID, EXPECTED_CONTRIBUTOR_COUNT);
    awaitIndexedResourceCounts(INSTANCE_SUBJECT, TENANT_ID, EXPECTED_SUBJECT_COUNT);
    awaitIndexedResourceCounts(LINKED_DATA_AUTHORITY, TENANT_ID, linkedDataAuthoritiesCount());
    awaitIndexedResourceCounts(LINKED_DATA_WORK, TENANT_ID, linkedDataWorksCount());
    awaitIndexedResourceCounts(LINKED_DATA_HUB, TENANT_ID, linkedDataHubsCount());
  }

  @Nested
  class BrowseAuthority extends BrowseAuthorityIT { }

  @Nested
  class BrowseCallNumber extends BrowseCallNumberIT { }

  @Nested
  class BrowseClassification extends BrowseClassificationIT { }

  @Nested
  class BrowseContributor extends BrowseContributorIT { }

  @Nested
  class BrowseSubject extends BrowseSubjectIT { }

  @Nested
  class FacetAuthority extends FacetAuthorityIT { }

  @Nested
  class FacetInstance extends FacetInstanceIT { }

  @Nested
  class FacetInstanceCallNumber extends FacetInstanceCallNumberIT { }

  @Nested
  class FacetInstanceContributor extends FacetInstanceContributorIT { }

  @Nested
  class FacetInstanceSubject extends FacetInstanceSubjectIT { }

  @Nested
  class SearchAuthority extends SearchAuthorityIT { }

  @Nested
  class SearchByEmptyValues extends SearchByEmptyValuesIT { }

  @Nested
  class SearchHoldings extends SearchHoldingsIT { }

  @Nested
  class SearchInstance extends SearchInstanceIT { }

  @Nested
  class SearchItem extends SearchItemIT { }

  @Nested
  class SearchLinkedDataAuthority extends SearchLinkedDataAuthorityIT { }

  @Nested
  class SearchLinkedDataHub extends SearchLinkedDataHubIT { }

  @Nested
  class SearchLinkedDataInstance extends SearchLinkedDataInstanceIT { }

  @Nested
  class SearchLinkedDataWork extends SearchLinkedDataWorkIT { }

  @Nested
  class SortAuthority extends SortAuthorityIT { }

  @Nested
  class SortInstance extends SortInstanceIT { }

  @Nested
  class SortItem extends SortItemIT { }

  @Nested
  class StreamResourceIds extends StreamResourceIdsIT { }

  private static final class SubResourceLockManager implements LockManager {

    private final SubResourcesLockRepository lockRepo;
    private Timestamp subjLock;
    private Timestamp classifLock;
    private Timestamp contribLock;
    private Timestamp cnLock;

    private SubResourceLockManager(SubResourcesLockRepository lockRepo) {
      this.lockRepo = lockRepo;
    }

    @Override
    public void lockAll() {
      cnLock = lockOrFail(ReindexEntityType.CALL_NUMBER);
      contribLock = lockOrFail(ReindexEntityType.CONTRIBUTOR);
      classifLock = lockOrFail(ReindexEntityType.CLASSIFICATION);
      subjLock = lockOrFail(ReindexEntityType.SUBJECT);
    }

    @Override
    public void unlockAll() {
      lockRepo.unlockSubResourceFenced(ReindexEntityType.CALL_NUMBER, cnLock, TENANT_ID, cnLock);
      lockRepo.unlockSubResourceFenced(ReindexEntityType.CONTRIBUTOR, contribLock, TENANT_ID, contribLock);
      lockRepo.unlockSubResourceFenced(ReindexEntityType.CLASSIFICATION, classifLock, TENANT_ID, classifLock);
      lockRepo.unlockSubResourceFenced(ReindexEntityType.SUBJECT, subjLock, TENANT_ID, subjLock);
    }

    private Timestamp lockOrFail(ReindexEntityType entityType) {
      return lockRepo.lockSubResource(entityType, TENANT_ID)
        .orElseThrow(() -> new IllegalStateException("Unable to lock %s resource".formatted(entityType)));
    }
  }
}
