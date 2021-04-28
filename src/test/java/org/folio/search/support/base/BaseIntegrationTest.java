package org.folio.search.support.base;

import static org.awaitility.Awaitility.await;
import static org.folio.search.sample.SampleInstances.getSemanticWeb;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.SearchUtils.X_OKAPI_TENANT_HEADER;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.awaitility.Duration;
import org.folio.search.domain.dto.Instance;
import org.folio.search.support.api.InventoryApi;
import org.folio.search.support.extension.EnableElasticSearch;
import org.folio.search.support.extension.EnableKafka;
import org.folio.search.support.extension.EnableOkapi;
import org.folio.search.support.extension.EnablePostgres;
import org.folio.search.support.extension.impl.OkapiConfiguration;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@Log4j2
@SpringBootTest
@AutoConfigureMockMvc
@EnablePostgres
@EnableKafka
@EnableElasticSearch
@EnableOkapi
public abstract class BaseIntegrationTest {
  protected static InventoryApi inventoryApi;
  private static OkapiConfiguration okapi;
  @Autowired protected MockMvc mockMvc;

  @BeforeAll
  static void setUpDefaultTenant(@Autowired MockMvc mockMvc,
    @Autowired KafkaTemplate<String, Object> kafkaTemplate) {

    inventoryApi = new InventoryApi(kafkaTemplate);
    setUpTenant(TENANT_ID, mockMvc, getSemanticWeb());
  }

  @BeforeAll
  public static void evictAllCaches(@Autowired CacheManager cacheManager) {
    for (String cacheName : cacheManager.getCacheNames()) {
      Optional.ofNullable(cacheManager.getCache(cacheName)).ifPresent(Cache::clear);
    }
  }

  @AfterAll
  static void removeDefaultTenant(@Autowired MockMvc mockMvc) {
    removeTenant(mockMvc, TENANT_ID);
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

  private static void checkThatElasticsearchAcceptResourcesFromKafka(
    String tenant, MockMvc mockMvc, String id) {

    await().atMost(Duration.ONE_MINUTE).pollInterval(Duration.ONE_SECOND).untilAsserted(() ->
      mockMvc.perform(get(searchInstancesByQuery("id={value}"), id)
        .headers(defaultHeaders(tenant)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("totalRecords", is(1)))
        .andExpect(jsonPath("instances[0].id", is(id))));
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
      .headers(defaultHeaders()))
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
    mockMvc.perform(post("/_/tenant")
      .content(asJsonString(new TenantAttributes().moduleTo("mod-search-1.0.0")))
      .headers(defaultHeaders(tenantName))
      .contentType(APPLICATION_JSON))
      .andExpect(status().isOk());

    for (Instance instance : instances) {
      inventoryApi.createInstance(tenantName, instance);
    }

    if (instances.length > 0) {
      checkThatElasticsearchAcceptResourcesFromKafka(tenantName, mockMvc,
        instances[instances.length - 1].getId());
    }
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
