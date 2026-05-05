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
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.sample.SampleInstances.getSemanticWebAsMap;

import java.util.Arrays;
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
import org.folio.api.search.SearchInstanceFilterIT;
import org.folio.api.search.SearchInstanceIT;
import org.folio.api.search.SearchItemIT;
import org.folio.api.search.SearchLinkedDataHubIT;
import org.folio.api.search.SearchLinkedDataInstanceIT;
import org.folio.api.search.SearchLinkedDataWorkIT;
import org.folio.api.search.SortAuthorityIT;
import org.folio.api.search.SortInstanceByTitleIT;
import org.folio.api.search.SortInstanceIT;
import org.folio.api.search.SortItemIT;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
class SearchBrowseIT extends BaseIntegrationTest {

  // ─── record counts (filled in Task 11 after all data is wired) ──────────────
  private static final int TOTAL_INSTANCES       = 0; // FILL IN TASK 11
  private static final int TOTAL_AUTHORITIES     = 0; // FILL IN TASK 11
  private static final int EXPECTED_CALL_NUMBER_COUNT    = 100;
  private static final int EXPECTED_CONTRIBUTOR_COUNT    = 13;
  private static final int EXPECTED_CLASSIFICATION_COUNT = 19;
  private static final int EXPECTED_SUBJECT_COUNT        = 28;

  @BeforeAll
  static void setUpSharedTenant(@Autowired SubResourcesLockRepository subResourcesLockRepository) {
    enableTenant(TENANT_ID);
    enableFeature(SEARCH_ALL_FIELDS);
    enableFeature(BROWSE_CALL_NUMBERS);
    enableFeature(BROWSE_CONTRIBUTORS);
    enableFeature(BROWSE_SUBJECTS);
    enableFeature(BROWSE_CLASSIFICATIONS);
    loadInstancesUnderLock(subResourcesLockRepository);
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(ResourceType.INSTANCE_CALL_NUMBER, TENANT_ID))
        .isEqualTo(EXPECTED_CALL_NUMBER_COUNT));
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(ResourceType.INSTANCE_CLASSIFICATION, TENANT_ID))
        .isEqualTo(EXPECTED_CLASSIFICATION_COUNT));
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(ResourceType.INSTANCE_CONTRIBUTOR, TENANT_ID))
        .isEqualTo(EXPECTED_CONTRIBUTOR_COUNT));
    await().atMost(ONE_MINUTE).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(countIndexDocument(ResourceType.INSTANCE_SUBJECT, TENANT_ID))
        .isEqualTo(EXPECTED_SUBJECT_COUNT));
    // further awaits (total) added in later tasks
  }

  private static void loadInstancesUnderLock(SubResourcesLockRepository repo) {
    final var cnLock = repo.lockSubResource(ReindexEntityType.CALL_NUMBER, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock CALL_NUMBER resource"));
    final var contribLock = repo.lockSubResource(ReindexEntityType.CONTRIBUTOR, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock CONTRIBUTOR resource"));
    final var classifLock = repo.lockSubResource(ReindexEntityType.CLASSIFICATION, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock CLASSIFICATION resource"));
    final var subjLock = repo.lockSubResource(ReindexEntityType.SUBJECT, TENANT_ID)
      .orElseThrow(() -> new IllegalStateException("Unable to lock SUBJECT resource"));

    inventoryApi.createInstance(TENANT_ID, getSemanticWebAsMap()); // Task 3
    Arrays.stream(SortInstanceIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
    Arrays.stream(SortInstanceByTitleIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
    Arrays.stream(SearchByEmptyValuesIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
    Arrays.stream(BrowseCallNumberIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i)); // Task 7
    Arrays.stream(BrowseClassificationIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i)); // Task 7

    // BrowseContributor group
    Arrays.stream(BrowseContributorIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));
    // Mutation: clear contributors from first instance so deletion re-index is tested
    BrowseContributorIT.INSTANCES[0].setContributors(emptyList());
    inventoryApi.updateInstance(TENANT_ID, BrowseContributorIT.INSTANCES[0]);

    // BrowseSubject group
    Arrays.stream(BrowseSubjectIT.INSTANCES).forEach(i -> inventoryApi.createInstance(TENANT_ID, i));

    repo.unlockSubResource(ReindexEntityType.CALL_NUMBER, cnLock, TENANT_ID);
    repo.unlockSubResource(ReindexEntityType.CONTRIBUTOR, contribLock, TENANT_ID);
    repo.unlockSubResource(ReindexEntityType.CLASSIFICATION, classifLock, TENANT_ID);
    repo.unlockSubResource(ReindexEntityType.SUBJECT, subjLock, TENANT_ID);
  }

  @AfterAll
  static void cleanUpSharedTenant() {
    removeTenant(TENANT_ID);
  }

  // ─── Nested stubs — each extends the corresponding *IT class ────────────────

  @Nested class SearchInstance extends SearchInstanceIT {}

  @Nested class SearchHoldings extends SearchHoldingsIT {}

  @Nested class SearchItem extends SearchItemIT {}

  @Nested class SearchByAllFields extends SearchByAllFieldsIT {}

  @Nested class SortInstance extends SortInstanceIT {}

  @Nested class SortInstanceByTitle extends SortInstanceByTitleIT {}

  @Nested class SearchByEmptyValues extends SearchByEmptyValuesIT {}

  @Nested class SearchInstanceFilter extends SearchInstanceFilterIT {}

  @Nested class SortItem extends SortItemIT {}

  @Nested class BrowseCallNumber extends BrowseCallNumberIT {}

  @Nested class BrowseClassification extends BrowseClassificationIT {}

  @Nested class BrowseContributor extends BrowseContributorIT {}

  @Nested class BrowseSubject extends BrowseSubjectIT {}

  @Nested class SearchAuthority extends SearchAuthorityIT {}

  @Nested class SearchAuthorityFilter extends SearchAuthorityFilterIT {}

  @Nested class SortAuthority extends SortAuthorityIT {}

  @Nested class BrowseAuthority extends BrowseAuthorityIT {}

  @Nested class SearchLinkedDataInstance extends SearchLinkedDataInstanceIT {}

  @Nested class SearchLinkedDataWork extends SearchLinkedDataWorkIT {}

  @Nested class SearchLinkedDataHub extends SearchLinkedDataHubIT {}
}
