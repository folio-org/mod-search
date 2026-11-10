package org.folio.indexing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsFullPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsStatus;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.ReindexItemRequest;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.api.ReindexInventoryStub;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.search.SearchHit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.core.type.TypeReference;

/**
 * Items-only reindex in a consortium: the central tenant starts it, item records of the central and member tenants
 * are written to the shared item index with their own tenant, and the shared flag of their instance.
 */
@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.item-index-enabled=true")
class ReindexItemRecordsConsortiumIT extends BaseIntegrationTest {

  private static final String CONSORTIUM_ID = "6a2e9f0b-5c1d-4f6e-8a31-0d7c2b9e4a55";
  private static final String CENTRAL_SCHEMA = CENTRAL_TENANT_ID + "_mod_search.";

  private static ReindexInventoryStub inventory;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(CENTRAL_TENANT_ID);
    enableTenant(MEMBER_TENANT_ID);
    enableTenant(MEMBER2_TENANT_ID);
    inventory = new ReindexInventoryStub(okapi.wireMockServer(), kafkaTemplate, objectMapper, false);
    mockConsortiumTenants();
  }

  @AfterAll
  static void cleanUp() {
    inventory.deactivate();
    removeTenant(MEMBER2_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @BeforeEach
  void setUp() {
    inventory.reset();
    jdbcTemplate.update("DELETE FROM " + CENTRAL_SCHEMA + "reindex_status");
    jdbcTemplate.update("DELETE FROM " + CENTRAL_SCHEMA + "merge_range");
  }

  @Test
  @SuppressWarnings("checkstyle:MethodLength")
  void shouldIndexItemsOfAllTenantsWithTenantAndSharedFlag() throws Exception {
    var sharedInstanceId = createInstance(CENTRAL_TENANT_ID);
    var localInstanceId = createInstance(MEMBER_TENANT_ID);
    var centralItem = rawItem(sharedInstanceId);
    var memberSharedItem = rawItem(sharedInstanceId);
    var memberLocalItem = rawItem(localInstanceId);
    inventory.itemCount(2);
    inventory.records(CENTRAL_TENANT_ID, List.of(centralItem));
    inventory.records(MEMBER_TENANT_ID, List.of(memberSharedItem, memberLocalItem));

    doPost(reindexItemRecordsFullPath(), CENTRAL_TENANT_ID, new ReindexItemRequest());
    awaitAssertion(() -> assertThat(statuses()).containsEntry("ITEM", "MERGE_COMPLETED"));
    awaitIndexedResourceCounts(ITEM, CENTRAL_TENANT_ID, 3);

    var documents = Arrays.stream(fetchAllDocuments(ITEM, CENTRAL_TENANT_ID))
      .collect(Collectors.toMap(SearchHit::getId, SearchHit::getSourceAsMap));
    assertThat(documents).containsOnlyKeys(
      centralItem.get("id") + "|" + CENTRAL_TENANT_ID,
      memberSharedItem.get("id") + "|" + MEMBER_TENANT_ID,
      memberLocalItem.get("id") + "|" + MEMBER_TENANT_ID);
    assertThat(documents.get(centralItem.get("id") + "|" + CENTRAL_TENANT_ID))
      .containsEntry("tenantId", CENTRAL_TENANT_ID).containsEntry("shared", true);
    assertThat(documents.get(memberSharedItem.get("id") + "|" + MEMBER_TENANT_ID))
      .containsEntry("tenantId", MEMBER_TENANT_ID).containsEntry("shared", true);
    assertThat(documents.get(memberLocalItem.get("id") + "|" + MEMBER_TENANT_ID))
      .containsEntry("tenantId", MEMBER_TENANT_ID).doesNotContainEntry("shared", true);

    // affiliation: items of the shared instance are visible to every tenant, items of a local instance only to its own
    awaitAssertion(() -> doSearch(itemSearchPath(), MEMBER2_TENANT_ID, Map.of("query",
      "instanceId==" + sharedInstanceId, "include", "tenantId"))
      .andExpect(jsonPath("$.totalRecords", is(2)))
      .andExpect(jsonPath("$.items[*].tenantId", containsInAnyOrder(CENTRAL_TENANT_ID, MEMBER_TENANT_ID))));
    doSearch(itemSearchPath(), "instanceId==" + localInstanceId, MEMBER2_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(0)));
    doSearch(itemSearchPath(), "instanceId==" + localInstanceId, MEMBER_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1)));
  }

  @Test
  void shouldRejectItemReindexFromMemberTenant() throws Exception {
    attemptPost(reindexItemRecordsFullPath(), MEMBER_TENANT_ID, new ReindexItemRequest())
      .andExpect(status().isBadRequest());

    assertThat(inventory.publishedRanges()).isEmpty();
  }

  /**
   * The default test mappings do not report a consortium id, so the consortium members are not resolvable.
   */
  private static void mockConsortiumTenants() {
    var wireMock = okapi.wireMockServer();
    wireMock.stubFor(get(urlPathEqualTo("/user-tenants"))
      .atPriority(1)
      .withQueryParam("tenantId", equalTo(CENTRAL_TENANT_ID))
      .willReturn(okJson("""
        {"userTenants": [{"centralTenantId": "%s", "consortiumId": "%s"}]}
        """.formatted(CENTRAL_TENANT_ID, CONSORTIUM_ID))));
    wireMock.stubFor(get(urlPathEqualTo("/consortia/" + CONSORTIUM_ID + "/tenants"))
      .willReturn(okJson("""
        {"tenants": [
          {"id": "%s", "isCentral": true},
          {"id": "%s", "isCentral": false},
          {"id": "%s", "isCentral": false}
        ]}
        """.formatted(CENTRAL_TENANT_ID, MEMBER_TENANT_ID, MEMBER2_TENANT_ID))));
  }

  private static String createInstance(String tenantId) {
    var instanceId = randomId();
    inventoryApi.createInstance(tenantId, new Instance().id(instanceId).title("title-" + instanceId));
    awaitAssertion(() -> doSearchInstances("id==" + instanceId, tenantId)
      .andExpect(jsonPath("$.totalRecords", is(1))));
    return instanceId;
  }

  private static Map<String, Object> rawItem(String instanceId) {
    var item = new LinkedHashMap<String, Object>();
    item.put("id", randomId());
    item.put("instanceId", instanceId);
    item.put("holdingsRecordId", randomId());
    item.put("status", mapOf("name", "Available"));
    return item;
  }

  @SneakyThrows
  private static Map<String, String> statuses() {
    var body = doGet(reindexItemRecordsStatus(), CENTRAL_TENANT_ID).andReturn().getResponse().getContentAsString();
    var items = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() { });
    return items.stream().collect(Collectors.toMap(
      item -> String.valueOf(item.get("entityType")), item -> String.valueOf(item.get("status"))));
  }
}
