package org.folio.search.support.base;

import static java.util.Arrays.asList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.awaitility.Duration.TWO_HUNDRED_MILLISECONDS;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
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
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.support.api.InventoryApi;
import org.folio.search.support.extension.EnableElasticSearch;
import org.folio.search.support.extension.EnableKafka;
import org.folio.search.support.extension.EnableOkapi;
import org.folio.search.support.extension.EnablePostgres;
import org.folio.search.support.extension.impl.OkapiConfiguration;
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

@Log4j2
@EnableOkapi
@EnableKafka
@EnablePostgres
@SpringBootTest
@EnableElasticSearch
@AutoConfigureMockMvc
public abstract class BaseIntegrationTest {

  protected static InventoryApi inventoryApi;
  private static OkapiConfiguration okapi;
  @Autowired protected MockMvc mockMvc;

  @BeforeAll
  static void setUpDefaultTenant(@Autowired KafkaTemplate<String, Object> kafkaTemplate) {
    setEnvProperty("folio-test");
    inventoryApi = new InventoryApi(kafkaTemplate);
  }

  @BeforeAll
  static void cleanUpCaches(@Autowired CacheManager cacheManager) {
    cacheManager.getCacheNames().forEach(name -> {
      var cache = cacheManager.getCache(name);
      if (cache != null) {
        cache.clear();
      }
    });
  }

  public static HttpHeaders defaultHeaders() {
    return defaultHeaders(TENANT_ID);
  }

  public static HttpHeaders defaultHeaders(String tenant) {
    final HttpHeaders httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(X_OKAPI_TENANT_HEADER, tenant);
    httpHeaders.add(XOkapiHeaders.URL, okapi.getOkapiUrl());

    return httpHeaders;
  }

  private static void checkThatElasticsearchAcceptResourcesFromKafka(String tenant, MockMvc mockMvc, int size) {
    await().atMost(ONE_MINUTE).pollInterval(TWO_HUNDRED_MILLISECONDS).untilAsserted(() ->
      mockMvc.perform(get("/search/instances").param("query", "cql.allRecords = 1").param("limit", "1")
          .headers(defaultHeaders(tenant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRecords", is(size))));
  }

  @SneakyThrows
  public ResultActions attemptPost(String uri, Object body) {
    return mockMvc.perform(post(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  public ResultActions attemptPut(String uri, Object body) {
    return mockMvc.perform(put(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  public ResultActions attemptDelete(String uri) {
    return mockMvc.perform(delete(uri)
      .headers(defaultHeaders())
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  public ResultActions doPost(String uri, Object body) {
    return attemptPost(uri, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public ResultActions doPut(String uri, Object body) {
    return attemptPut(uri, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public ResultActions doGet(String uri, Object... args) {
    return doGet(mockMvc, uri, args);
  }

  @SneakyThrows
  public static ResultActions doGet(MockMvc mockMvc, String uri, Object... args) {
    return mockMvc.perform(get(uri, args)
        .headers(defaultHeaders())
        .header("Accept", "application/json;charset=UTF-8"))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  public ResultActions doDelete(String uri, Object... args) {
    return doDelete(mockMvc, uri, args);
  }

  @SneakyThrows
  public static ResultActions doDelete(MockMvc mockMvc, String uri, Object... args) {
    return mockMvc.perform(delete(uri, args)
        .headers(defaultHeaders()))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantName, MockMvc mockMvc, Instance... instances) {
    setUpTenant(tenantName, mockMvc, () -> {}, asList(instances),
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(String tenantName, MockMvc mockMvc, Map<String, Object>... instances) {
    setUpTenant(tenantName, mockMvc, () -> {}, asList(instances),
      instance -> inventoryApi.createInstance(tenantName, instance));
  }

  @SneakyThrows
  protected static void setUpTenant(String tenant, MockMvc mockMvc,
    Runnable postTenantAction, Instance... instances) {
    setUpTenant(tenant, mockMvc, postTenantAction, asList(instances),
      instance -> inventoryApi.createInstance(tenant, instance));
  }

  @SafeVarargs
  @SneakyThrows
  protected static void setUpTenant(String tenant, MockMvc mockMvc,
    Runnable postTenantAction, Map<String, Object>... instances) {
    setUpTenant(tenant, mockMvc, postTenantAction, asList(instances),
      instance -> inventoryApi.createInstance(tenant, instance));
  }

  @SneakyThrows
  private static <T> void setUpTenant(String tenant, MockMvc mockMvc,
    Runnable postTenantAction, List<T> instances, Consumer<T> consumer) {
    mockMvc.perform(post("/_/tenant")
        .content(asJsonString(new TenantAttributes().moduleTo("mod-search-1.0.0")))
        .headers(defaultHeaders(tenant))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());

    postTenantAction.run();

    instances.forEach(consumer);

    if (instances.size() > 0) {
      checkThatElasticsearchAcceptResourcesFromKafka(tenant, mockMvc, instances.size());
    }
  }

  @SneakyThrows
  protected static <T> void enableFeature(String tenant, TenantConfiguredFeature feature, MockMvc mockMvc) {
    mockMvc.perform(post(ApiEndpoints.featureConfig())
        .headers(defaultHeaders(tenant))
        .content(asJsonString(new FeatureConfig().feature(feature).enabled(true))))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static void removeTenant(MockMvc mockMvc, String tenant) {
    mockMvc.perform(delete("/_/tenant")
        .headers(defaultHeaders(tenant)))
      .andExpect(status().isNoContent());
  }

  public static WireMockServer getWireMock() {
    return okapi.getWireMockServer();
  }
}
