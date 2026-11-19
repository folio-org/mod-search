package org.folio.support.base;

import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.testdata.ConsortiumTestDataManager.CALL_NUMBER_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.CLASSIFICATION_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.CONTRIBUTOR_COUNT;
import static org.folio.support.testdata.ConsortiumTestDataManager.SUBJECT_COUNT;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.scheduled.ScheduledInstanceSubResourcesService;
import org.folio.support.sample.SampleInstances;
import org.folio.support.testdata.ConsortiumTestDataManager;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Sets up the shared ECS inventory data set for the ECS suites ({@code SearchBrowseConsortiumSuiteIT},
 * {@code BrowseCallNumberFromItemsConsortiumIT}): enables the browse features, loads the data set with
 * {@link ConsortiumTestDataManager}, waits until it is indexed and configures the LC call number browse.
 *
 * <p>The central, member and member2 tenants must be enabled before, and the item index must be enabled
 * ({@code folio.search-config.indexing.item-index-enabled=true}).
 */
public final class EcsInventorySetup {

  private EcsInventorySetup() { }

  /**
   * Loads the ECS inventory data set and waits until it is indexed.
   *
   * @param maxBatchIdsCount maximum number of ids the batch items endpoint accepts per request
   *                         ({@code folio.search-config.max-search-batch-request-ids-count})
   */
  public static void setUp(SubResourcesLockRepository lockRepository,
                           ScheduledInstanceSubResourcesService scheduledSubResourcesService,
                           int maxBatchIdsCount) {
    enableBrowseFeatures();
    var lockManager = new SubResourceLockManager(lockRepository, CENTRAL_TENANT_ID, ReindexEntityType.CALL_NUMBER,
      ReindexEntityType.CONTRIBUTOR, ReindexEntityType.CLASSIFICATION, ReindexEntityType.SUBJECT,
      ReindexEntityType.ITEM, ReindexEntityType.INSTANCE);
    ConsortiumTestDataManager.loadAll(lockManager, BaseSharedTest.inventoryApi,
      (tenantId, instanceIds) -> BaseSharedTest.assertSearchByIdsCount(
        instanceSearchPath(), instanceIds, instanceIds.size(), tenantId),
      scheduledSubResourcesService::persistChildren,
      () -> awaitIndexed(maxBatchIdsCount));
    BaseSharedTest.updateCnConfig(List.of(UUID.fromString(ConsortiumTestDataManager.CALL_NUMBER_LC_TYPE_ID)),
      BrowseOptionType.LC, ShelvingOrderAlgorithmType.LC, CENTRAL_TENANT_ID);
  }

  private static void enableBrowseFeatures() {
    BaseSharedTest.enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_CALL_NUMBERS);
    BaseSharedTest.enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_CLASSIFICATIONS);
    BaseSharedTest.enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_CONTRIBUTORS);
    BaseSharedTest.enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_SUBJECTS);
  }

  private static void awaitIndexed(int maxBatchIdsCount) {
    BaseSharedTest.awaitAssertion(() -> BaseSharedTest.doSearch(instanceSearchPath(), MEMBER_TENANT_ID,
        Map.of(BaseSharedTest.QUERY_PARAM, "id==" + SampleInstances.getSemanticWebId(),
          BaseSharedTest.EXPAND_ALL_PARAM, "true"))
      .andExpectAll(SampleInstances.getSemanticWebMatchers().toArray(new ResultMatcher[0])));
    awaitIndexedCount(ResourceType.INSTANCE, ConsortiumTestDataManager.instancesCount());
    // Holdings and items are sent through their own Kafka topics, so the instance documents can be searchable before
    // their holdings and items are added to them; the consortium holdings/items endpoints read them from there.
    BaseSharedTest.awaitAssertion(() -> BaseSharedTest.doGet(ApiEndpoints.consortiumHoldingsSearchPath(),
        CENTRAL_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(ConsortiumTestDataManager.holdingsCount()))));
    var itemIds = ConsortiumTestDataManager.itemIds();
    // the batch items endpoint accepts a limited number of ids per request, so the ids are checked in chunks
    for (var from = 0; from < itemIds.size(); from += maxBatchIdsCount) {
      awaitItemsSearchable(itemIds.subList(from, Math.min(from + maxBatchIdsCount, itemIds.size())));
    }
    // the item index holds the items of all tenants in the central tenant index
    awaitIndexedCount(ResourceType.ITEM, itemIds.size());
    awaitIndexedCount(ResourceType.INSTANCE_CALL_NUMBER, CALL_NUMBER_COUNT);
    awaitIndexedCount(ResourceType.INSTANCE_CONTRIBUTOR, CONTRIBUTOR_COUNT);
    awaitIndexedCount(ResourceType.INSTANCE_CLASSIFICATION, CLASSIFICATION_COUNT);
    awaitIndexedCount(ResourceType.INSTANCE_SUBJECT, SUBJECT_COUNT);
  }

  private static void awaitIndexedCount(ResourceType resourceType, int expectedCount) {
    BaseSharedTest.awaitIndexedResourceCounts(resourceType, CENTRAL_TENANT_ID, expectedCount);
  }

  private static void awaitItemsSearchable(List<String> itemIds) {
    var itemsRequest = new BatchIdsDto().identifierType(BatchIdsDto.IdentifierTypeEnum.ID)
      .identifierValues(itemIds);
    BaseSharedTest.awaitAssertion(() -> BaseSharedTest.doPost(ApiEndpoints.consortiumBatchItemsSearchPath(),
        CENTRAL_TENANT_ID, itemsRequest)
      .andExpect(jsonPath("$.totalRecords", is(itemIds.size()))));
  }
}
