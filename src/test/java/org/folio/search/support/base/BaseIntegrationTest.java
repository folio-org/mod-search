package org.folio.search.support.base;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS;
import static org.folio.search.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.doIfNotNull;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.setEnvProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.support.api.InventoryApi;
import org.folio.search.support.extension.EnableElasticSearch;
import org.folio.search.support.extension.EnableKafka;
import org.folio.search.support.extension.EnableOkapi;
import org.folio.search.support.extension.EnablePostgres;
import org.folio.search.support.extension.impl.OkapiConfiguration;
import org.folio.search.utils.TestUtils;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Log4j2
@EnableOkapi
@EnableKafka
@EnablePostgres
@SpringBootTest
@EnableElasticSearch
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  protected static MockMvc mockMvc;
  protected static InventoryApi inventoryApi;
  protected static KafkaTemplate<String, Object> kafkaTemplate;
  protected static OkapiConfiguration okapi;

  @BeforeAll
  static void setUpDefaultTenant(
    @Autowired MockMvc mockMvc,
    @Autowired KafkaTemplate<String, Object> kafkaTemplate) {
    setEnvProperty("folio-test");
    BaseIntegrationTest.mockMvc = mockMvc;
    BaseIntegrationTest.kafkaTemplate = kafkaTemplate;
    BaseIntegrationTest.inventoryApi = new InventoryApi(kafkaTemplate);
  }

  @BeforeAll
  static void cleanUpCaches(@Autowired CacheManager cacheManager) {
    TestUtils.cleanUpCaches(cacheManager);
  }

  public static HttpHeaders defaultHeaders() {
    return defaultHeaders(TENANT_ID);
  }

  public static HttpHeaders defaultHeaders(String tenant) {
    var httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(X_OKAPI_TENANT_HEADER, tenant);
    httpHeaders.add(XOkapiHeaders.URL, okapi.getOkapiUrl());

    return httpHeaders;
  }

  public static WireMockServer getWireMock() {
    return okapi.getWireMockServer();
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
    setUpTenant(TENANT_ID, instances);
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, Instance... instances) {
    setUpTenant(tenantName, instanceSearchPath(), () -> {}, asList(instances), instances.length,
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  protected static void setUpTenant(int expectedCount, Authority... authorities) {
    setUpTenant(TENANT_ID, authoritySearchPath(), () -> {}, asList(authorities), expectedCount,
      record -> kafkaTemplate.send(inventoryAuthorityTopic(), record.getId(), resourceEvent(null, null, record)));
  }

  @SneakyThrows
  protected static void setUpTenant(String tenant, Runnable postInitAction, Instance... instances) {
    setUpTenant(tenant, instanceSearchPath(), postInitAction, asList(instances), instances.length,
      instance -> inventoryApi.createInstance(tenant, instance));
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Map<String, Object>... rawRecords) {
    setUpTenant(type, TENANT_ID, rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, Map<String, Object>... rawRecords) {
    setUpTenant(type, tenant, () -> {}, rawRecords.length, rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Integer expectedCount, Map<String, Object>... rawRecords) {
    setUpTenant(type, TENANT_ID, () -> {}, expectedCount, rawRecords);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, Runnable postInitAction, Map<String, Object>... records) {
    setUpTenant(type, TENANT_ID, postInitAction, records.length, records);
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(Class<?> type, String tenant, Runnable postInitAction, Integer expectedCount,
    Map<String, Object>... records) {
    if (type.equals(Instance.class)) {
      setUpTenant(tenant, instanceSearchPath(), postInitAction, asList(records), expectedCount,
        instance -> inventoryApi.createInstance(tenant, instance));
    }

    if (type.equals(Authority.class)) {
      setUpTenant(tenant, authoritySearchPath(), postInitAction, asList(records), expectedCount,
        record -> kafkaTemplate.send(inventoryAuthorityTopic(tenant), resourceEvent(null, null, record)));
    }
  }

  @SneakyThrows
  private static <T> void setUpTenant(String tenant, String validationPath, Runnable postInitAction,
    List<T> records, Integer expectedCount, Consumer<T> consumer) {
    enableTenant(tenant);
    postInitAction.run();
    records.forEach(consumer);
    if (records.size() > 0) {
      checkThatEventsFromKafkaAreIndexed(tenant, validationPath, expectedCount);
    }
  }

  @SneakyThrows
  @SuppressWarnings("SameParameterValue")
  protected static void enableFeature(TenantConfiguredFeature feature) {
    enableFeature(TENANT_ID, feature);
  }

  @SneakyThrows
  @SuppressWarnings("SameParameterValue")
  protected static void enableFeature(String tenantId, TenantConfiguredFeature feature) {
    mockMvc.perform(post(ApiEndpoints.featureConfig())
        .headers(defaultHeaders(tenantId))
        .content(asJsonString(new FeatureConfig().feature(feature).enabled(true))))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(new TenantAttributes().moduleTo("mod-search")))
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

  protected static void checkThatEventsFromKafkaAreIndexed(String tenantId, String path, int size) {
    await().atMost(ONE_MINUTE).pollInterval(TWO_HUNDRED_MILLISECONDS).untilAsserted(() ->
      doSearch(path, tenantId, "cql.allRecords=1", 1, null, null).andExpect(jsonPath("$.totalRecords", is(size))));
  }

  protected static String prepareQuery(String queryTemplate, String value) {
    return value != null ? queryTemplate.replace("{value}", value) : queryTemplate;
  }
}
