package org.folio.support.base;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_HUB;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_INSTANCE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.inventoryAuthorityTopic;
import static org.folio.support.TestConstants.linkedDataHubTopic;
import static org.folio.support.TestConstants.linkedDataInstanceTopic;
import static org.folio.support.TestConstants.linkedDataWorkTopic;
import static org.folio.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataHubSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataInstanceSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataWorkSearchPath;
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.removeEnvProperty;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.folio.support.utils.TestUtils.setEnvProperty;
import static org.hamcrest.Matchers.is;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.folio.search.SearchApplication;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.LinkedDataHub;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.extension.EnableKafka;
import org.folio.spring.testing.extension.EnableOkapi;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.extension.impl.OkapiConfiguration;
import org.folio.support.api.InventoryApi;
import org.folio.support.extension.EnableElasticSearch;
import org.folio.support.utils.TestUtils;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@EnableOkapi
@EnableKafka
@EnablePostgres
@EnableElasticSearch
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_CLASS)
@SpringBootTest(classes = SearchApplication.class)
public abstract class BaseIntegrationTest {

  protected static MockMvc mockMvc;
  protected static InventoryApi inventoryApi;
  protected static KafkaTemplate<String, ResourceEvent> kafkaTemplate;
  protected static OkapiConfiguration okapi;
  protected static RestHighLevelClient elasticClient;
  protected static CacheManager cacheManager;

  public static HttpHeaders defaultHeaders() {
    return defaultHeaders(TENANT_ID);
  }

  public static HttpHeaders defaultHeaders(String tenant) {
    var httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, tenant);
    httpHeaders.add(XOkapiHeaders.URL, okapi.getOkapiUrl());

    return httpHeaders;
  }

  @SneakyThrows
  public static ResultActions attemptGet(String uri, Object... args) {
    return attemptGet(uri, TENANT_ID, args);
  }

  @SneakyThrows
  public static ResultActions attemptGet(String uri, String tenantId, Object... args) {
    return mockMvc.perform(get(uri, args)
      .headers(defaultHeaders(tenantId))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  public static ResultActions doGet(String uri, Object... args) {
    return mockMvc.perform(get(uri, args)
        .headers(defaultHeaders())
        .accept("application/json;charset=UTF-8"))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static ResultActions doGet(MockHttpServletRequestBuilder request) {
    return mockMvc.perform(request
        .headers(defaultHeaders())
        .accept("application/json;charset=UTF-8"))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public static SearchHit[] fetchAllDocuments(ResourceType resourceType, String tenantId) {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(matchAllQuery()))
      .indices(getIndexName(resourceType, tenantId));
    return elasticClient.search(searchRequest, DEFAULT).getHits().getHits();
  }

  @SneakyThrows
  public static void deleteAllDocuments(ResourceType resourceType, String tenantId) {
    var request = new DeleteByQueryRequest(getIndexName(resourceType, tenantId));
    request.setQuery(matchAllQuery());
    elasticClient.deleteByQuery(request, DEFAULT);
  }

  public static void assertCountByIds(String path, List<String> ids, int expected) {
    var query = exactMatchAny(CqlQueryParam.ID, ids).toString();
    awaitAssertion(() -> doSearch(path, query).andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  public static void assertCountByQuery(String path, String template, String value, int expected) {
    awaitAssertion(() -> doSearch(path, prepareQuery(template, value))
      .andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  public static void awaitAssertion(ThrowingRunnable runnable) {
    Awaitility.await()
      .atMost(ONE_MINUTE)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }

  @SneakyThrows
  protected static ResultActions attemptPost(String uri, Object body) {
    return mockMvc.perform(post(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptPut(String uri, Object body) {
    return mockMvc.perform(put(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptDelete(String uri) {
    return mockMvc.perform(delete(uri)
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions doPost(String uri, Object body) {
    return attemptPost(uri, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doPut(String uri, Object body) {
    return attemptPut(uri, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doDelete(String uri, Object... args) {
    return mockMvc.perform(delete(uri, args)
        .headers(defaultHeaders()))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query) {
    return doSearch(instanceSearchPath(), TENANT_ID, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, String include) {
    return doSearch(instanceSearchPath(), TENANT_ID, Map.of("query", query, "include", include));
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, boolean expandAll) {
    return doSearch(instanceSearchPath(), TENANT_ID, Map.of("query", query, "expandAll", String.valueOf(expandAll)));
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, int limit, int offset) {
    return doSearch(instanceSearchPath(), TENANT_ID,
      Map.of("query", query, "limit", String.valueOf(limit), "offset", String.valueOf(offset))
    );
  }

  @SneakyThrows
  protected static ResultActions attemptSearchByInstances(String query) {
    return attemptSearch(instanceSearchPath(), TENANT_ID, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions doSearchByAuthorities(String query) {
    return doSearch(authoritySearchPath(), TENANT_ID, Map.of("query", query));
  }

  protected static ResultActions doSearchByLinkedDataInstance(String query) {
    return doSearchByLinkedDataInstance(TENANT_ID, query);
  }

  @SneakyThrows
  protected static ResultActions doSearchByLinkedDataInstance(String tenantId, String query) {
    return doSearch(linkedDataInstanceSearchPath(), tenantId, Map.of("query", query));
  }

  protected static ResultActions doSearchByLinkedDataWork(String query) {
    return doSearchByLinkedDataWork(TENANT_ID, query);
  }

  @SneakyThrows
  protected static ResultActions doSearchByLinkedDataWork(String tenantId, String query) {
    return doSearch(linkedDataWorkSearchPath(), tenantId, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions doSearchByLinkedDataWorkWithoutInstances(String query) {
    return doSearch(linkedDataWorkSearchPath(), TENANT_ID, Map.of("query", query, "omitInstances", "true"));
  }

  @SneakyThrows
  protected static ResultActions doSearchByLinkedDataHub(String query) {
    return doSearch(linkedDataHubSearchPath(), TENANT_ID, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions attemptSearchByAuthorities(String query) {
    return attemptSearch(authoritySearchPath(), TENANT_ID, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions doSearch(String path, String query) {
    return doSearch(path, TENANT_ID, Map.of("query", query));
  }

  @SneakyThrows
  protected static ResultActions doSearch(String path, String tenantId, Map<String, String> queryParams) {
    return attemptSearch(path, tenantId, queryParams).andExpect(status().isOk());
  }

  protected static long countIndexDocument(ResourceType resource, String tenantId) throws IOException {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(0))
      .indices(getIndexName(resource.getName(), tenantId));
    var searchResponse = elasticClient.search(searchRequest, DEFAULT);
    return Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value();
  }

  protected static String getIndexId(ResourceType resource) throws IOException {
    var getIndexResponse = elasticClient.indices().get(new GetIndexRequest(getIndexName(resource, TENANT_ID)), DEFAULT);
    return getIndexResponse.getSetting(getIndexResponse.getIndices()[0], "index.uuid");
  }

  protected static long countDefaultIndexDocument(ResourceType resource) throws IOException {
    return countIndexDocument(resource, TENANT_ID);
  }

  protected static void cleanUpIndex(ResourceType resource, String tenantId) throws IOException {
    var request = new DeleteByQueryRequest(getIndexName(resource.getName(), tenantId));
    request.setQuery(matchAllQuery());
    elasticClient.deleteByQuery(request, DEFAULT);
  }

  @SneakyThrows
  protected static ResultActions attemptSearch(String path, String tenantId, Map<String, String> queryParams) {
    var requestBuilder = get(path);
    queryParams.forEach(requestBuilder::param);
    return mockMvc.perform(requestBuilder
      .headers(defaultHeaders(tenantId))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  protected static void setUpTenant(Instance... instances) {
    setUpTenant(emptyList(), instances);
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, Instance... instances) {
    setUpTenant(tenantName, emptyList(), instances);
  }

  @SneakyThrows
  protected static void setUpTenant(List<ResultMatcher> matchers, Instance... instances) {
    setUpTenant(TENANT_ID, matchers, instances);
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, List<ResultMatcher> matchers, Instance... instances) {
    setUpTenant(tenantName, instanceSearchPath(), () -> { }, asList(instances), instances.length, matchers,
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  protected static void setUpTenant(int expectedCount, Authority... authorities) {
    setUpTenant(TENANT_ID, authoritySearchPath(), () -> { }, asList(authorities), expectedCount, emptyList(),
      rec -> kafkaTemplate.send(inventoryAuthorityTopic(), rec.getId(), resourceEvent(null, AUTHORITY, rec)));
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Map<String, Object>... rawRecords) {
    setUpTenant(type, TENANT_ID, rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, List<ResultMatcher> matchers, Map<String, Object>... rawRecords) {
    setUpTenant(type, TENANT_ID, matchers, rawRecords);
  }

  @SneakyThrows
  protected static void setUpTenant(List<TestData> testDataList, String tenant) {
    enableTenant(tenant);
    for (TestData testData : testDataList) {
      var type = testData.type();
      var testRecords = testData.testRecords();
      var expectedCount = testData.expectedCount();

      if (type.equals(Instance.class)) {
        saveRecords(tenant, instanceSearchPath(), testRecords, expectedCount,
          instance -> inventoryApi.createInstance(tenant, instance));
      }

      if (type.equals(Authority.class)) {
        saveRecords(tenant, authoritySearchPath(), testRecords, expectedCount,
          rec -> kafkaTemplate.send(inventoryAuthorityTopic(tenant), resourceEvent(null, AUTHORITY, rec)));
      }
    }
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, Map<String, Object>... rawRecords) {
    setUpTenant(type, tenant, () -> { }, rawRecords.length, emptyList(), rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, List<ResultMatcher> matchers,
                                    Map<String, Object>... rawRecords) {
    setUpTenant(type, tenant, () -> { }, rawRecords.length, matchers, rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Integer expectedCount, Map<String, Object>... rawRecords) {
    setUpTenant(type, TENANT_ID, () -> { }, expectedCount, emptyList(), rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Runnable postInitAction,
                                    @NotNull List<ResultMatcher> matchers, Map<String, Object>... records) {
    setUpTenant(type, TENANT_ID, postInitAction, records.length, matchers, records);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, Runnable postInitAction, Integer expectedCount,
                                    List<ResultMatcher> matchers, Map<String, Object>... records) {
    String searchPath;
    Consumer<Object> consumer;

    if (type.equals(Instance.class)) {
      searchPath = instanceSearchPath();
      consumer = instance -> inventoryApi.createInstance(tenant, (Map<String, Object>) instance);
    } else if (type.equals(Authority.class)) {
      searchPath = authoritySearchPath();
      consumer = authority -> kafkaTemplate.send(inventoryAuthorityTopic(tenant), event(authority, AUTHORITY, tenant));
    } else if (type.equals(LinkedDataInstance.class)) {
      searchPath = linkedDataInstanceSearchPath();
      consumer = ldInstance -> kafkaTemplate.send(linkedDataInstanceTopic(tenant),
        event(ldInstance, LINKED_DATA_INSTANCE, tenant));
    } else if (type.equals(LinkedDataWork.class)) {
      searchPath = linkedDataWorkSearchPath();
      consumer = ldWork -> kafkaTemplate.send(linkedDataWorkTopic(tenant), event(ldWork, LINKED_DATA_WORK, tenant));
    } else if (type.equals(LinkedDataHub.class)) {
      searchPath = linkedDataHubSearchPath();
      consumer = ldHub -> kafkaTemplate.send(linkedDataHubTopic(tenant), event(ldHub, LINKED_DATA_HUB, tenant));
    } else {
      throw new IllegalArgumentException("Unsupported type: " + type.getName());
    }

    setUpTenant(tenant, searchPath, postInitAction, asList(records), expectedCount, matchers, consumer);
  }

  @SneakyThrows
  protected static <T> void setUpTenant(String tenant, String validationPath, Runnable postInitAction,
                                        List<T> records, Integer expectedCount, List<ResultMatcher> matchers,
                                        Consumer<T> consumer) {
    enableTenant(tenant);
    postInitAction.run();
    saveRecords(tenant, validationPath, records, expectedCount, matchers, consumer);
  }

  protected static <T> void saveRecords(String tenant, String validationPath, List<T> records, Integer expectedCount,
                                        Consumer<T> consumer) {
    saveRecords(tenant, validationPath, records, expectedCount, emptyList(), consumer);
  }

  protected static <T> void saveRecords(String tenant, String validationPath, List<T> records, Integer expectedCount,
                                        List<ResultMatcher> matchers, Consumer<T> consumer) {
    records.forEach(consumer);
    if (!records.isEmpty()) {
      checkThatEventsFromKafkaAreIndexed(tenant, validationPath, expectedCount, matchers);
    }
  }

  @SneakyThrows
  protected static void enableFeature(TenantConfiguredFeature feature) {
    enableFeature(TENANT_ID, feature);
  }

  @SneakyThrows
  @SuppressWarnings("SameParameterValue")
  protected static void enableFeature(String tenantId, TenantConfiguredFeature feature) {
    mockMvc.perform(post(ApiEndpoints.featureConfigPath())
        .headers(defaultHeaders(tenantId))
        .content(asJsonString(new FeatureConfig().feature(feature).enabled(true))))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-search");
    if (CENTRAL_TENANT_ID.equals(tenant) || MEMBER_TENANT_ID.equals(tenant)) {
      tenantAttributes.addParametersItem(new Parameter("centralTenantId").value(CENTRAL_TENANT_ID));
    }
    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(tenantAttributes))
        .headers(defaultHeaders(tenant))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static void removeTenant() {
    removeTenant(TENANT_ID);
  }

  @SneakyThrows
  protected static void removeTenant(String tenantId) {
    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(new TenantAttributes().moduleFrom("mod-search").purge(true)))
        .headers(defaultHeaders(tenantId)))
      .andExpect(status().isNoContent());
  }

  protected static void checkThatEventsFromKafkaAreIndexed(String tenantId, String path, int size,
                                                           List<ResultMatcher> matchers) {
    Awaitility.await().atMost(ONE_MINUTE).pollInterval(TWO_HUNDRED_MILLISECONDS).untilAsserted(() ->
      doSearch(path, tenantId, Map.of("query", "cql.allRecords=1", "expandAll", "true"))
        .andExpect(jsonPath("$.totalRecords", is(size)))
        .andExpectAll(matchers.toArray(new ResultMatcher[0])));
  }

  protected static String prepareQuery(String queryTemplate, String value) {
    return value != null ? queryTemplate.replace("{value}", value) : queryTemplate;
  }

  static ResourceEvent event(Object object, ResourceType resourceType, String tenant) {
    return resourceEvent(tenant, resourceType, CREATE, object);
  }

  @BeforeAll
  static void setUpDefaultTenant(
    @Autowired MockMvc mockMvc,
    @Autowired KafkaTemplate<String, ResourceEvent> kafkaTemplate,
    @Autowired RestHighLevelClient restHighLevelClient,
    @Autowired CacheManager cacheManager) {
    setEnvProperty("folio-test");
    BaseIntegrationTest.mockMvc = mockMvc;
    BaseIntegrationTest.kafkaTemplate = kafkaTemplate;
    BaseIntegrationTest.inventoryApi = new InventoryApi(kafkaTemplate);
    BaseIntegrationTest.elasticClient = restHighLevelClient;
    BaseIntegrationTest.cacheManager = cacheManager;
  }

  @BeforeAll
  static void cleanUpCaches(@Autowired CacheManager cacheManager) {
    TestUtils.cleanUpCaches(cacheManager);
  }

  @AfterAll
  static void afterAll() {
    removeEnvProperty();
  }

  public record TestData(Class<?> type, List<Map<String, Object>> testRecords, int expectedCount) { }
}
