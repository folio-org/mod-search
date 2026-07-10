package org.folio.support.base;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.authoritySearchPath;
import static org.folio.support.base.ApiEndpoints.browseConfigPath;
import static org.folio.support.base.ApiEndpoints.indexRecordsPath;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataAuthoritySearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataHubSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataInstanceSearchPath;
import static org.folio.support.base.ApiEndpoints.linkedDataWorkSearchPath;
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.folio.support.utils.TestUtils.mockCallNumberTypes;
import static org.folio.support.utils.TestUtils.mockClassificationTypes;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.is;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.SneakyThrows;
import org.awaitility.core.ThrowingRunnable;
import org.folio.search.domain.dto.BrowseConfig;
import org.folio.search.domain.dto.BrowseOptionType;
import org.folio.search.domain.dto.BrowseType;
import org.folio.search.domain.dto.FeatureConfig;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ShelvingOrderAlgorithmType;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.client.CqlQueryParam;
import org.folio.search.model.types.ResourceType;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.extension.impl.OkapiConfiguration;
import org.folio.support.api.InventoryApi;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.search.SearchHit;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared test state and helper methods. Contains all static fields and utilities used by IT classes,
 * but no Spring Boot infrastructure annotations. This allows abstract IT classes to extend this class
 * without inheriting {@code @DirtiesContext}, which would otherwise close the Spring context
 * prematurely when nested inside {@link BaseIntegrationTest}.
 */
public abstract class BaseSharedTest {

  protected static final String QUERY_PARAM = "query";
  protected static final String LIMIT_PARAM = "limit";
  protected static final String OFFSET_PARAM = "offset";
  protected static final String INCLUDE_PARAM = "include";
  protected static final String EXPAND_ALL_PARAM = "expandAll";
  protected static final String PRECEDING_RECORDS_COUNT_PARAM = "precedingRecordsCount";

  protected static final String[] COLLECTION_IGNORING_FIELDS = {"items.id"};
  protected static final String[] ENTRY_IGNORING_FIELDS = {"id"};

  protected static MockMvc mockMvc;
  protected static InventoryApi inventoryApi;
  protected static KafkaTemplate<String, Object> kafkaTemplate;
  protected static ObjectMapper objectMapper;
  protected static OkapiConfiguration okapi;
  protected static RestHighLevelClient elasticClient;
  protected static CacheManager cacheManager;

  protected static HttpHeaders defaultHeaders(String tenant) {
    var httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, tenant);
    httpHeaders.add(XOkapiHeaders.URL, okapi.getOkapiUrl());

    return httpHeaders;
  }

  @SneakyThrows
  protected static ResultActions attemptGet(String uri, String tenantId, Object... args) {
    return mockMvc.perform(get(uri, args)
      .headers(defaultHeaders(tenantId))
      .accept(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptPost(String uri, String tenantId, Object body) {
    return mockMvc.perform(post(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders(tenantId))
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptPut(String uri, String tenantId, Object body) {
    return mockMvc.perform(put(uri)
      .content(asJsonString(body))
      .headers(defaultHeaders(tenantId))
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptDelete(String uri, String tenantId) {
    return mockMvc.perform(delete(uri)
      .headers(defaultHeaders(tenantId))
      .contentType(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptSearch(String path, String tenantId, Map<String, String> queryParams) {
    var requestBuilder = get(path);
    queryParams.forEach(requestBuilder::param);
    return mockMvc.perform(requestBuilder
      .headers(defaultHeaders(tenantId))
      .accept(APPLICATION_JSON));
  }

  @SneakyThrows
  protected static ResultActions attemptSearchInstances(String query, String tenantId) {
    return attemptSearch(instanceSearchPath(), tenantId, Map.of(QUERY_PARAM, query));
  }

  @SneakyThrows
  protected static ResultActions attemptSearchAuthorities(String query, String tenantId) {
    return attemptSearch(authoritySearchPath(), tenantId, Map.of(QUERY_PARAM, query));
  }

  @SneakyThrows
  protected static ResultActions doGet(String uri, String tenantId, Object... args) {
    return attemptGet(uri, tenantId, args)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doGet(MockHttpServletRequestBuilder request, String tenantId) {
    return mockMvc.perform(request
        .headers(defaultHeaders(tenantId))
        .accept("application/json;charset=UTF-8"))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doPost(String uri, String tenantId, Object body) {
    return attemptPost(uri, tenantId, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doPut(String uri, String tenantId, Object body) {
    return attemptPut(uri, tenantId, body)
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static ResultActions doDelete(String uri, String tenantId, Object... args) {
    return mockMvc.perform(delete(uri, args)
        .headers(defaultHeaders(tenantId)))
      .andExpect(status().isNoContent());
  }

  @SneakyThrows
  protected static ResultActions doSearchInstances(String query, String tenantId) {
    return doSearch(instanceSearchPath(), query, tenantId);
  }

  @SneakyThrows
  protected static ResultActions doSearchInstances(String query, String tenantId, String include) {
    return doSearch(instanceSearchPath(), tenantId, Map.of(QUERY_PARAM, query, INCLUDE_PARAM, include));
  }

  @SneakyThrows
  protected static ResultActions doSearchInstances(String query, String tenantId, int limit, int offset) {
    return doSearch(instanceSearchPath(), tenantId,
      Map.of(QUERY_PARAM, query, LIMIT_PARAM, String.valueOf(limit), OFFSET_PARAM, String.valueOf(offset))
    );
  }

  @SneakyThrows
  protected static ResultActions doSearchInstancesExpandAll(String query, String tenantId) {
    return doSearch(instanceSearchPath(), tenantId,
      Map.of(QUERY_PARAM, query, EXPAND_ALL_PARAM, String.valueOf(true)));
  }

  @SneakyThrows
  protected static ResultActions doSearchAuthorities(String query, String tenantId) {
    return doSearch(authoritySearchPath(), query, tenantId);
  }

  @SneakyThrows
  protected static ResultActions doSearchLinkedDataInstance(String query, String tenantId) {
    return doSearch(linkedDataInstanceSearchPath(), query, tenantId);
  }

  @SneakyThrows
  protected static ResultActions doSearchLinkedDataWork(String query, String tenantId) {
    return doSearch(linkedDataWorkSearchPath(), query, tenantId);
  }

  @SneakyThrows
  protected static ResultActions doSearchLinkedDataWorkWithoutInstances(String query, String tenantId) {
    return doSearch(linkedDataWorkSearchPath(), tenantId, Map.of(QUERY_PARAM, query, "omitInstances", "true"));
  }

  @SneakyThrows
  protected static ResultActions doSearchLinkedDataHub(String query, String tenantId) {
    return doSearch(linkedDataHubSearchPath(), query, tenantId);
  }

  @SneakyThrows
  protected static ResultActions doSearchLinkedDataAuthority(String query, String tenantId) {
    return doSearch(linkedDataAuthoritySearchPath(), query, tenantId);
  }

  @SneakyThrows
  protected static ResultActions doSearch(String path, String query, String tenantId) {
    return doSearch(path, tenantId, Map.of(QUERY_PARAM, query));
  }

  @SneakyThrows
  protected static ResultActions doSearch(String path, String tenantId, Map<String, String> queryParams) {
    return attemptSearch(path, tenantId, queryParams).andExpect(status().isOk());
  }

  protected static void indexRecords(List<ResourceEvent> events, String tenantId) {
    doPost(indexRecordsPath(), tenantId, events).andReturn();
  }

  @SneakyThrows
  protected static SearchHit[] fetchAllDocuments(ResourceType resourceType, String tenantId) {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(matchAllQuery()))
      .indices(getIndexName(resourceType, tenantId));
    return elasticClient.search(searchRequest, DEFAULT).getHits().getHits();
  }

  @SneakyThrows
  protected static void deleteAllDocuments(ResourceType resourceType, String tenantId) {
    var request = new DeleteByQueryRequest(getIndexName(resourceType, tenantId));
    request.setQuery(matchAllQuery());
    elasticClient.deleteByQuery(request, DEFAULT);
  }

  protected static void assertSearchByIdsCount(String path, List<String> ids, int expected, String tenantId) {
    var query = exactMatchAny(CqlQueryParam.ID, ids).toString();
    awaitAssertion(() -> doSearch(path, query, tenantId).andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  protected static void assertSearchByQueryCount(String path, String query, String value,
                                                 int expected, String tenantId) {
    awaitAssertion(() -> doSearch(path, prepareQuery(query, value), tenantId)
      .andExpect(jsonPath("$.totalRecords", is(expected))));
  }

  protected static void awaitIndexedResourceCounts(ResourceType resourceType, String tenantId, int expectedCount) {
    awaitAssertion(() ->
      assertThat(countIndexDocument(resourceType, tenantId))
        .as("%s index document count should reach %d", resourceType, expectedCount)
        .isEqualTo(expectedCount));
  }

  protected static void awaitIndexedResourceCounts(QueryBuilder query, ResourceType resourceType,
                                                   String tenantId, int expectedCount) {
    awaitAssertion(() ->
      assertThat(countIndexDocument(query, resourceType, tenantId))
        .as("%s index document count by query %s should reach %d", resourceType, query, expectedCount)
        .isEqualTo(expectedCount));
  }

  protected static void awaitAssertion(ThrowingRunnable runnable) {
    await()
      .atMost(ONE_MINUTE)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(runnable);
  }

  protected static long countIndexDocument(ResourceType resource, String tenantId) {
    return countIndexDocument(QueryBuilders.matchAllQuery(), resource, tenantId);
  }

  @SneakyThrows
  protected static long countIndexDocument(QueryBuilder query, ResourceType resource, String tenantId) {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(query).trackTotalHits(true).from(0).size(0))
      .indices(getIndexName(resource.getName(), tenantId));
    var searchResponse = elasticClient.search(searchRequest, DEFAULT);
    return Objects.requireNonNull(searchResponse.getHits().getTotalHits()).value();
  }

  protected static String getIndexId(ResourceType resource, String tenantId) throws IOException {
    var getIndexResponse = elasticClient.indices().get(new GetIndexRequest(getIndexName(resource, tenantId)), DEFAULT);
    return getIndexResponse.getSetting(getIndexResponse.getIndices()[0], "index.uuid");
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
  protected static void enableFeature(String tenantId, TenantConfiguredFeature feature) {
    mockMvc.perform(post(ApiEndpoints.featureConfigPath())
        .headers(defaultHeaders(tenantId))
        .content(asJsonString(new FeatureConfig().feature(feature).enabled(true))))
      .andExpect(status().isOk());
  }

  @SneakyThrows
  protected static void enableTenant(String tenant) {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-search");
    if (CENTRAL_TENANT_ID.equals(tenant) || MEMBER_TENANT_ID.equals(tenant) || MEMBER2_TENANT_ID.equals(tenant)) {
      tenantAttributes.addParametersItem(new Parameter("centralTenantId").value(CENTRAL_TENANT_ID));
    }
    mockMvc.perform(post("/_/tenant", randomId())
        .content(asJsonString(tenantAttributes))
        .headers(defaultHeaders(tenant))
        .contentType(APPLICATION_JSON))
      .andExpect(status().isNoContent());
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
    awaitAssertion(() ->
      doSearch(path, tenantId, Map.of(QUERY_PARAM, SearchUtils.ALL_RECORDS_QUERY, EXPAND_ALL_PARAM, "true"))
        .andExpect(jsonPath("$.totalRecords", is(size)))
        .andExpectAll(matchers.toArray(new ResultMatcher[0])));
  }

  protected static String prepareQuery(String queryTemplate, String value) {
    return value != null ? queryTemplate.replace("{value}", value) : queryTemplate;
  }

  protected static ResourceEvent event(Object object, ResourceType resourceType, String tenant) {
    return resourceEvent(tenant, resourceType, CREATE, object);
  }

  protected static void updateCnConfig(List<UUID> typeIds, BrowseOptionType browseOptionType,
                                       ShelvingOrderAlgorithmType algorithmType, String tenantId) {
    updateBrowseConfig(typeIds, BrowseType.INSTANCE_CALL_NUMBER, browseOptionType, algorithmType,
      uuids -> mockCallNumberTypes(okapi.wireMockServer(), uuids.toArray(new UUID[0])), tenantId);
  }

  protected static void updateClassConfig(List<UUID> typeIds, BrowseOptionType browseOptionType,
                                          ShelvingOrderAlgorithmType algorithmType, String tenantId) {
    updateBrowseConfig(typeIds, BrowseType.INSTANCE_CLASSIFICATION, browseOptionType, algorithmType,
      uuids -> mockClassificationTypes(okapi.wireMockServer(), uuids.toArray(new UUID[0])), tenantId);
  }

  protected static void updateBrowseConfig(List<UUID> typeIds, BrowseType browseType,
                                           BrowseOptionType browseOptionType, ShelvingOrderAlgorithmType algorithm,
                                           Function<List<UUID>, MappingBuilder> mocker, String tenantId) {
    var config = new BrowseConfig()
      .id(browseOptionType)
      .shelvingAlgorithm(algorithm)
      .typeIds(typeIds);

    var stub = mocker.apply(typeIds);
    doPut(browseConfigPath(browseType, browseOptionType), tenantId, config);
    okapi.wireMockServer().removeStub(stub);
  }
}
