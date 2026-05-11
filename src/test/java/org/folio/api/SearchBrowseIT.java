package org.folio.api;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.SEARCH_ALL_FIELDS;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataHubSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataInstanceSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataWorkSearchPath;

import java.sql.Timestamp;
import java.util.List;
import org.folio.api.browse.BrowseAuthorityIT;
import org.folio.api.browse.BrowseCallNumberIT;
import org.folio.api.browse.BrowseClassificationIT;
import org.folio.api.browse.BrowseContributorIT;
import org.folio.api.browse.BrowseSubjectIT;
import org.folio.api.search.SearchAuthorityFilterIT;
import org.folio.api.search.SearchAuthorityIT;
import org.folio.api.search.SearchByAllFieldsIT;
import org.folio.api.search.SearchByEmptyValuesIT;
import org.folio.api.search.SearchHoldingsIT;
import org.folio.api.search.SearchInstanceFacetIT;
import org.folio.api.search.SearchInstanceIT;
import org.folio.api.search.SearchItemIT;
import org.folio.api.search.SearchLinkedDataHubIT;
import org.folio.api.search.SearchLinkedDataInstanceIT;
import org.folio.api.search.SearchLinkedDataWorkIT;
import org.folio.api.search.SortAuthorityIT;
import org.folio.api.search.SortInstanceByTitleIT;
import org.folio.api.search.SortInstanceIT;
import org.folio.api.search.SortItemIT;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.scheduled.ScheduledInstanceSubResourcesService;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.folio.support.testdata.SharedTestDataManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class SearchBrowseIT extends BaseIntegrationTest {

  private static final int TOTAL_INSTANCES = 96;
  private static final int TOTAL_AUTHORITIES = 117;
  private static final int EXPECTED_CALL_NUMBER_COUNT = 116;
  private static final int EXPECTED_CONTRIBUTOR_COUNT = 68;
  private static final int EXPECTED_CLASSIFICATION_COUNT = 92;
  private static final int EXPECTED_SUBJECT_COUNT = 50;

  @BeforeAll
  static void setUpSharedTenant(
    @Autowired SubResourcesLockRepository lockRepo,
    @Autowired ScheduledInstanceSubResourcesService scheduledSubResourcesService) {
    enableTenant(TENANT_ID);
    enableFeature(SEARCH_ALL_FIELDS);
    enableFeature(BROWSE_CALL_NUMBERS);
    enableFeature(BROWSE_CONTRIBUTORS);
    enableFeature(BROWSE_SUBJECTS);
    enableFeature(BROWSE_CLASSIFICATIONS);
    SharedTestDataManager.loadInventory(TENANT_ID, createLockManager(lockRepo),
      scheduledSubResourcesService::persistChildren, SearchBrowseIT::indexRecords);
    SharedTestDataManager.loadAuthorities(TENANT_ID, SearchBrowseIT::indexRecords);
    awaitSubResourceIndexing();
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, instanceSearchPath(), TOTAL_INSTANCES, emptyList());
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, authoritySearchPath(), TOTAL_AUTHORITIES, emptyList());
    loadLinkedData();
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(TENANT_ID);
  }

  private static SharedTestDataManager.LockManager createLockManager(SubResourcesLockRepository lockRepo) {
    return new BrowseLockManager(lockRepo);
  }

  private static void awaitSubResourceIndexing() {
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(INSTANCE_CALL_NUMBER, TENANT_ID))
        .isEqualTo(EXPECTED_CALL_NUMBER_COUNT));
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(INSTANCE_CLASSIFICATION, TENANT_ID))
        .isEqualTo(EXPECTED_CLASSIFICATION_COUNT));
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(INSTANCE_CONTRIBUTOR, TENANT_ID))
        .isEqualTo(EXPECTED_CONTRIBUTOR_COUNT));
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(INSTANCE_SUBJECT, TENANT_ID))
        .isEqualTo(EXPECTED_SUBJECT_COUNT));
  }

  private static void loadLinkedData() {
    SharedTestDataManager.loadLinkedData(TENANT_ID, SearchBrowseIT::indexRecords);
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, linkedDataInstanceSearchPath(),
      SearchLinkedDataInstanceIT.INSTANCE_SAMPLES.size(), emptyList());
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, linkedDataWorkSearchPath(),
      SearchLinkedDataWorkIT.WORK_SAMPLES.size(), emptyList());
    checkThatEventsFromKafkaAreIndexed(TENANT_ID, linkedDataHubSearchPath(),
      SearchLinkedDataHubIT.HUB_SAMPLES.size(), emptyList());
  }

  private static void indexRecords(List<ResourceEvent> events) {
    try {
      var mvcResult = doPost("/search/index/records", events).andReturn();
      System.out.println(mvcResult.getResponse().getContentAsString());
    } catch (Exception e) {
      throw new RuntimeException("Failed to index records", e);
    }
  }

  @Nested
  class SearchInstance extends SearchInstanceIT { }

  @Nested
  class SearchHoldings extends SearchHoldingsIT { }

  @Nested
  class SearchItem extends SearchItemIT { }

  @Nested
  class SearchByAllFields extends SearchByAllFieldsIT { }

  @Nested
  class SortInstance extends SortInstanceIT { }

  @Nested
  class SortInstanceByTitle extends SortInstanceByTitleIT { }

  @Nested
  class SearchByEmptyValues extends SearchByEmptyValuesIT { }

  @Nested
  class SearchInstanceFacet extends SearchInstanceFacetIT { }

  @Nested
  class SortItem extends SortItemIT { }

  @Nested
  class BrowseCallNumber extends BrowseCallNumberIT { }

  @Nested
  class BrowseClassification extends BrowseClassificationIT { }

  @Nested
  class BrowseContributor extends BrowseContributorIT { }

  @Nested
  class BrowseSubject extends BrowseSubjectIT { }

  @Nested
  class SearchAuthority extends SearchAuthorityIT { }

  @Nested
  class SearchAuthorityFilter extends SearchAuthorityFilterIT { }

  @Nested
  class SortAuthority extends SortAuthorityIT { }

  @Nested
  class BrowseAuthority extends BrowseAuthorityIT { }

  @Nested
  class SearchLinkedDataInstance extends SearchLinkedDataInstanceIT { }

  @Nested
  class SearchLinkedDataWork extends SearchLinkedDataWorkIT { }

  @Nested
  class SearchLinkedDataHub extends SearchLinkedDataHubIT { }

  private static final class BrowseLockManager implements SharedTestDataManager.LockManager {

    private final SubResourcesLockRepository lockRepo;
    private Timestamp subjLock;
    private Timestamp classifLock;
    private Timestamp contribLock;
    private Timestamp cnLock;

    private BrowseLockManager(SubResourcesLockRepository lockRepo) {
      this.lockRepo = lockRepo;
    }

    @Override
    public void lockAll() {
      cnLock = lockRepo.lockSubResource(ReindexEntityType.CALL_NUMBER, TENANT_ID)
        .orElseThrow(() -> new IllegalStateException("Unable to lock CALL_NUMBER resource"));
      contribLock = lockRepo.lockSubResource(ReindexEntityType.CONTRIBUTOR, TENANT_ID)
        .orElseThrow(() -> new IllegalStateException("Unable to lock CONTRIBUTOR resource"));
      classifLock = lockRepo.lockSubResource(ReindexEntityType.CLASSIFICATION, TENANT_ID)
        .orElseThrow(() -> new IllegalStateException("Unable to lock CLASSIFICATION resource"));
      subjLock = lockRepo.lockSubResource(ReindexEntityType.SUBJECT, TENANT_ID)
        .orElseThrow(() -> new IllegalStateException("Unable to lock SUBJECT resource"));
    }

    @Override
    public void unlockAll() {
      lockRepo.unlockSubResource(ReindexEntityType.CALL_NUMBER, cnLock, TENANT_ID);
      lockRepo.unlockSubResource(ReindexEntityType.CONTRIBUTOR, contribLock, TENANT_ID);
      lockRepo.unlockSubResource(ReindexEntityType.CLASSIFICATION, classifLock, TENANT_ID);
      lockRepo.unlockSubResource(ReindexEntityType.SUBJECT, subjLock, TENANT_ID);
    }
  }
}
