package org.folio.support.base;

import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CALL_NUMBERS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CLASSIFICATIONS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_CONTRIBUTORS;
import static org.folio.search.domain.dto.TenantConfiguredFeature.BROWSE_SUBJECTS;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.INSTANCE_CLASSIFICATION;
import static org.folio.search.model.types.ResourceType.INSTANCE_CONTRIBUTOR;
import static org.folio.search.model.types.ResourceType.INSTANCE_SUBJECT;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
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
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.BatchIdsDto;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
import org.folio.search.service.scheduled.ScheduledInstanceSubResourcesService;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public abstract class BaseConsortiumIntegrationTest extends BaseSharedTest {

  /**
   * Creates the shared ECS inventory data set ({@link org.folio.support.testdata.ConsortiumTestDataManager}) and waits
   * until it is indexed: shared instances in the central tenant, local instances in the member and member2 tenants,
   * with holdings, items, call numbers, contributors, classifications and subjects. Enables the browse features of the
   * central tenant and the LC call number browse configuration the data set is checked with.
   *
   * <p>Used by the suites that run the consortium browse/search tests on this data set; the central, member and
   * member2 tenants must be enabled before. Sub-resources are locked so that their scheduled processing does not race
   * with the data load.
   */
  public static void setUpEcsInventory(SubResourcesLockRepository lockRepository,
                                       ScheduledInstanceSubResourcesService scheduledSubResourcesService) {
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CALL_NUMBERS);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CLASSIFICATIONS);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_CONTRIBUTORS);
    enableFeature(CENTRAL_TENANT_ID, BROWSE_SUBJECTS);

    var lockManager = new SubResourceLockManager(lockRepository, CENTRAL_TENANT_ID, ReindexEntityType.CALL_NUMBER,
      ReindexEntityType.CONTRIBUTOR, ReindexEntityType.CLASSIFICATION, ReindexEntityType.SUBJECT,
      ReindexEntityType.ITEM, ReindexEntityType.INSTANCE);
    loadInventory(lockManager, inventoryApi,
      BaseConsortiumIntegrationTest::awaitInstancesVisible, scheduledSubResourcesService::persistChildren);
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

  @SneakyThrows
  public static ResultActions tryGet(String uri, Object... args) {
    return tryGet(uri, MEMBER_TENANT_ID, args);
  }

  @SneakyThrows
  public static ResultActions tryGet(String uri, String tenantHeader, Object... args) {
    return mockMvc.perform(get(uri, args)
      .headers(defaultHeaders(tenantHeader))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  public static ResultActions doGet(String uri, Object... args) {
    return doGet(uri, MEMBER_TENANT_ID, args);
  }

  @SneakyThrows
  public static ResultActions doGet(String uri, String tenantHeader, Object... args) {
    return tryGet(uri, tenantHeader, args)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static ResultActions doGet(MockHttpServletRequestBuilder request) {
    return doGet(request, MEMBER_TENANT_ID);
  }

  @SneakyThrows
  public static ResultActions doGet(MockHttpServletRequestBuilder request, String tenantHeader) {
    return mockMvc.perform(request
        .headers(defaultHeaders(tenantHeader))
        .accept("application/json;charset=UTF-8"))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static ResultActions tryPut(String uri, String tenantHeader, Object body) {
    return mockMvc.perform(put(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders(tenantHeader))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  public static ResultActions doPut(String uri, String tenantHeader, Object body) {
    return tryPut(uri, tenantHeader, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static ResultActions tryPost(String uri, Object body) {
    return tryPost(uri, MEMBER_TENANT_ID, body);
  }

  @SneakyThrows
  public static ResultActions tryPost(String uri, String tenantHeader, Object body) {
    return mockMvc.perform(post(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders(tenantHeader))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  public static ResultActions doPost(String uri, String tenantHeader, Object body) {
    return tryPost(uri, tenantHeader, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query) {
    return doSearch(instanceSearchPath(), MEMBER_TENANT_ID, Map.of(QUERY_PARAM, query));
  }
}
