package org.folio.search.support.base;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TWO_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TWO_MINUTES;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_AUTHORITY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.support.base.ApiEndpoints.linkedDataAuthoritySearchPath;
import static org.folio.search.support.base.ApiEndpoints.linkedDataSearchPath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestConstants.linkedDataAuthorityTopic;
import static org.folio.search.utils.TestConstants.linkedDataWorkTopic;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.doIfNotNull;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.removeEnvProperty;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.setEnvProperty;
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
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.types.ResourceType;
import org.folio.search.support.api.InventoryApi;
import org.folio.search.support.api.InventoryViewResponseBuilder;
import org.folio.search.support.extension.EnableElasticSearch;
import org.folio.search.utils.TestUtils;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.extension.EnableKafka;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.extension.impl.OkapiConfiguration;
import org.folio.spring.testing.extension.impl.OkapiExtension;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.reindex.DeleteByQueryRequest;
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

@Log4j2
@EnableKafka
@EnablePostgres
@SpringBootTest
@EnableElasticSearch
@AutoConfigureMockMvc
@DirtiesContext(classMode = AFTER_CLASS)
public abstract class BaseIntegrationTest {

  protected static MockMvc mockMvc;
  protected static InventoryApi inventoryApi;
  protected static KafkaTemplate<String, ResourceEvent> kafkaTemplate;
  protected static OkapiConfiguration okapi;
  protected static RestHighLevelClient elasticClient;
  protected static CacheManager cacheManager;

  @RegisterExtension
  static OkapiExtension okapiExtension =
    new OkapiExtension(new InventoryViewResponseBuilder());

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
    return doSearch(instanceSearchPath(), TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, boolean expandAll) {
    return doSearch(instanceSearchPath(), TENANT_ID, query, null, null, expandAll);
  }

  @SneakyThrows
  protected static ResultActions doSearchByInstances(String query, int limit, int offset) {
    return doSearch(instanceSearchPath(), TENANT_ID, query, limit, offset, null);
  }

  @SneakyThrows
  protected static ResultActions attemptSearchByInstances(String query) {
    return attemptSearch(instanceSearchPath(), TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearchByAuthorities(String query) {
    return doSearch(authoritySearchPath(), TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearchByLinkedDataWork(String query) {
    return doSearch(linkedDataSearchPath(), TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearchByLinkedDataAuthority(String query) {
    return doSearch(linkedDataAuthoritySearchPath(), TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions attemptSearchByAuthorities(String query) {
    return attemptSearch(authoritySearchPath(), TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearch(String path, String query) {
    return doSearch(path, TENANT_ID, query, null, null, null);
  }

  @SneakyThrows
  protected static ResultActions doSearch(
    String path, String tenantId, String query, Integer limit, Integer offset, Boolean expandAll) {
    return attemptSearch(path, tenantId, query, limit, offset, expandAll).andExpect(status().isOk());
  }

  protected static long countIndexDocument(ResourceType resource, String tenantId) throws IOException {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(matchAllQuery()).trackTotalHits(true).from(0).size(0))
      .indices(getIndexName(resource.getName(), tenantId));
    var searchResponse = elasticClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits().value;
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
  protected static ResultActions attemptSearch(
    String path, String tenantId, String query, Integer limit, Integer offset, Boolean expandAll) {
    var requestBuilder = get(path);
    doIfNotNull(limit, value -> requestBuilder.queryParam("limit", String.valueOf(value)));
    doIfNotNull(offset, value -> requestBuilder.queryParam("offset", String.valueOf(value)));
    doIfNotNull(expandAll, value -> requestBuilder.queryParam("expandAll", String.valueOf(value)));

    return mockMvc.perform(requestBuilder.queryParam("query", query)
      .headers(defaultHeaders(tenantId))
      .accept("application/json;charset=UTF-8"));
  }

  @SneakyThrows
  protected static void setUpTenant(Instance... instances) {
    setUpTenant(emptyList(), instances);
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
      record -> kafkaTemplate.send(inventoryAuthorityTopic(), record.getId(), resourceEvent(null, AUTHORITY, record)));
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
      var type = testData.getType();
      var testRecords = testData.getTestRecords();
      var expectedCount = testData.getExpectedCount();

      if (type.equals(Instance.class)) {
        saveRecords(tenant, instanceSearchPath(), testRecords, expectedCount,
          instance -> inventoryApi.createInstance(tenant, instance));
      }

      if (type.equals(Authority.class)) {
        saveRecords(tenant, authoritySearchPath(), testRecords, expectedCount,
          record -> kafkaTemplate.send(inventoryAuthorityTopic(tenant), resourceEvent(null, AUTHORITY, record)));
      }
    }
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, Map<String, Object>... rawRecords) {
    setUpTenant(type, tenant, () -> {
    }, rawRecords.length, emptyList(), rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, List<ResultMatcher> matchers,
                                    Map<String, Object>... rawRecords) {
    setUpTenant(type, tenant, () -> {
    }, rawRecords.length, matchers, rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Integer expectedCount, Map<String, Object>... rawRecords) {
    setUpTenant(type, TENANT_ID, () -> {
    }, expectedCount, emptyList(), rawRecords);
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
    if (type.equals(Instance.class)) {
      setUpTenant(tenant, instanceSearchPath(), postInitAction, asList(records), expectedCount, matchers,
        instance -> inventoryApi.createInstance(tenant, instance));
    }

    if (type.equals(Authority.class)) {
      setUpTenant(tenant, authoritySearchPath(), postInitAction, asList(records), expectedCount, matchers,
        authority -> kafkaTemplate.send(inventoryAuthorityTopic(tenant), resourceEvent(null, AUTHORITY, authority)));
    }

    if (type.equals(LinkedDataWork.class)) {
      setUpTenant(tenant, linkedDataSearchPath(), postInitAction, asList(records), expectedCount, matchers,
        ldWork -> kafkaTemplate.send(linkedDataWorkTopic(tenant), resourceEvent(null, LINKED_DATA_WORK, ldWork)));
    }

    if (type.equals(LinkedDataAuthority.class)) {
      setUpTenant(tenant, linkedDataAuthoritySearchPath(), postInitAction, asList(records), expectedCount, matchers,
        ldAuthority -> kafkaTemplate.send(linkedDataAuthorityTopic(tenant),
          resourceEvent(null, LINKED_DATA_AUTHORITY, ldAuthority)));
    }
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
  @SuppressWarnings("SameParameterValue")
  protected static void disableFeature(TenantConfiguredFeature feature) {
    disableFeature(TENANT_ID, feature);
  }

  @SneakyThrows
  @SuppressWarnings("SameParameterValue")
  protected static void disableFeature(String tenantId, TenantConfiguredFeature feature) {
    mockMvc.perform(delete(ApiEndpoints.featureConfigPath(feature))
        .headers(defaultHeaders(tenantId))
        .content(asJsonString(new FeatureConfig().feature(feature).enabled(false))))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-search");

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
    await().logging().atMost(TWO_MINUTES).pollInterval(TWO_HUNDRED_MILLISECONDS).untilAsserted(() ->
      doSearch(path, tenantId, "cql.allRecords=1", size, null, true)
      .andExpect(jsonPath("$.totalRecords", is(size)))
      .andExpectAll(matchers.toArray(new ResultMatcher[0])));
  }

  protected static String prepareQuery(String queryTemplate, String value) {
    return value != null ? queryTemplate.replace("{value}", value) : queryTemplate;
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

  @Getter
  @RequiredArgsConstructor
  protected static class TestData {

    private final Class<?> type;
    private final List<Map<String, Object>> testRecords;
    private final int expectedCount;
  }

}
