package org.folio.indexing;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.CALL_NUMBER_FIELD;
import static org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor.EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD;
import static org.folio.search.utils.SearchUtils.EFFECTIVE_LOCATION_ID_FIELD;
import static org.folio.search.utils.SearchUtils.HOLDINGS_RECORD_ID_FIELD;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SHARED_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.TENANT_ID_FIELD_NAME;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFullPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexStatusPath;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.IndexRebuildRequest;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.api.ItemRecordsInventoryStub;
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
 * Item records reindex in a consortium: records of all tenants go to the shared item index, with their own tenant and
 * the shared flag derived from the instance source.
 */
@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.item-index-enabled=true")
class ItemRecordsReindexConsortiumIT extends BaseIntegrationTest {

  private static final String CONSORTIUM_ID = "6a2e9f0b-5c1d-4f6e-8a31-0d7c2b9e4a55";
  private static final String SCHEMA = CENTRAL_TENANT_ID + "_mod_search.";
  private static final String CALL_NUMBER_FULL_PATH = "/search/index/call-number-records/reindex/full";
  private static final String CALL_NUMBER_STATUS_PATH = "/search/index/call-number-records/reindex/status";

  private static ItemRecordsInventoryStub inventory;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(CENTRAL_TENANT_ID);
    enableTenant(MEMBER_TENANT_ID);
    enableTenant(MEMBER2_TENANT_ID);
    inventory = new ItemRecordsInventoryStub(okapi.wireMockServer(), kafkaTemplate, objectMapper, false);
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
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_state");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_range");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_state WHERE resource = 'call_number'");
  }

  @Test
  void shouldIndexItemsOfAllTenantsWithTenantAndSharedFlag() {
    var centralItem = rawItem("MARC");
    var memberSharedItem = rawItem("CONSORTIUM-MARC");
    var memberLocalItem = rawItem("FOLIO");
    inventory.itemCount(1);
    inventory.records(CENTRAL_TENANT_ID, List.of(centralItem));
    inventory.records(MEMBER_TENANT_ID, List.of(memberSharedItem, memberLocalItem));

    doPost(itemRecordsReindexFullPath(), CENTRAL_TENANT_ID, new IndexRebuildRequest());
    awaitAllCompleted();
    awaitIndexedResourceCounts(ITEM, CENTRAL_TENANT_ID, 3);

    var documents = documents();
    assertThat(documents.get(centralItem.get("id") + "|" + CENTRAL_TENANT_ID))
      .containsEntry(TENANT_ID_FIELD_NAME, CENTRAL_TENANT_ID).containsEntry(SHARED_FIELD_NAME, true);
    assertThat(documents.get(memberSharedItem.get("id") + "|" + MEMBER_TENANT_ID))
      .containsEntry(TENANT_ID_FIELD_NAME, MEMBER_TENANT_ID).containsEntry(SHARED_FIELD_NAME, true);
    assertThat(documents.get(memberLocalItem.get("id") + "|" + MEMBER_TENANT_ID))
      .containsEntry(TENANT_ID_FIELD_NAME, MEMBER_TENANT_ID).doesNotContainEntry(SHARED_FIELD_NAME, true);
  }

  @Test
  void shouldReloadOnlyDocumentsOfSingleMember() {
    var centralItem = rawItem("MARC");
    var oldMemberItem = rawItem("FOLIO");
    inventory.itemCount(1);
    inventory.records(CENTRAL_TENANT_ID, List.of(centralItem));
    inventory.records(MEMBER_TENANT_ID, List.of(oldMemberItem));
    doPost(itemRecordsReindexFullPath(), CENTRAL_TENANT_ID, new IndexRebuildRequest());
    awaitAllCompleted();
    awaitIndexedResourceCounts(ITEM, CENTRAL_TENANT_ID, 2);

    var newMemberItem = rawItem("FOLIO");
    inventory.records(MEMBER_TENANT_ID, List.of(newMemberItem));
    doPost(itemRecordsReindexFullPath(), CENTRAL_TENANT_ID,
      new IndexRebuildRequest().tenantId(MEMBER_TENANT_ID));
    awaitAllCompleted();

    awaitAssertion(() -> assertThat(documents()).containsOnlyKeys(
      centralItem.get("id") + "|" + CENTRAL_TENANT_ID, newMemberItem.get("id") + "|" + MEMBER_TENANT_ID));
  }

  @Test
  void shouldRejectMemberRerunWhenIndexIsOutdated() throws Exception {
    attemptPost(itemRecordsReindexFullPath(), CENTRAL_TENANT_ID,
      new IndexRebuildRequest().tenantId(MEMBER_TENANT_ID))
      .andExpect(status().isBadRequest());

    assertThat(inventory.publishedRanges()).isEmpty();
  }

  @Test
  void shouldRejectRequestFromMemberTenant() throws Exception {
    attemptPost(itemRecordsReindexFullPath(), MEMBER_TENANT_ID, new IndexRebuildRequest())
      .andExpect(status().isBadRequest());

    assertThat(inventory.publishedRanges()).isEmpty();
  }

  @Test
  void shouldRecomputeCallNumbersOfSingleMember() {
    enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_CALL_NUMBERS);
    var centralItem = callNumberItem("SHARED", "MARC");
    var memberShared = callNumberItem("SHARED", "CONSORTIUM-MARC");
    var memberOnly = callNumberItem("MEMBER-ONLY", "FOLIO");
    inventory.itemCount(1);
    inventory.records(CENTRAL_TENANT_ID, List.of(centralItem));
    inventory.records(MEMBER_TENANT_ID, List.of(memberShared, memberOnly));
    doPost(itemRecordsReindexFullPath(), CENTRAL_TENANT_ID, new IndexRebuildRequest());
    awaitAllCompleted();
    awaitIndexedResourceCounts(ITEM, CENTRAL_TENANT_ID, 3);
    runCallNumbers(new IndexRebuildRequest());
    awaitAssertion(() -> assertThat(callNumberTenants()).containsOnlyKeys("SHARED", "MEMBER-ONLY"));

    inventory.records(MEMBER_TENANT_ID, List.of());
    doPost(itemRecordsReindexFullPath(), CENTRAL_TENANT_ID, new IndexRebuildRequest().tenantId(MEMBER_TENANT_ID));
    awaitAllCompleted();
    awaitIndexedResourceCounts(ITEM, CENTRAL_TENANT_ID, 1);
    runCallNumbers(new IndexRebuildRequest().tenantId(MEMBER_TENANT_ID));

    awaitAssertion(() -> assertThat(callNumberTenants()).containsOnlyKeys("SHARED"));
    assertThat(callNumberTenants().get("SHARED")).containsExactly(CENTRAL_TENANT_ID);
  }

  private static void runCallNumbers(IndexRebuildRequest request) {
    doPost(CALL_NUMBER_FULL_PATH, CENTRAL_TENANT_ID, request);
    awaitAssertion(() -> assertThat(callNumberStatuses().values())
      .doesNotContain("IN_PROGRESS", "FINALIZING", "FAILED").contains("COMPLETED"));
  }

  @SneakyThrows
  private static Map<String, String> callNumberStatuses() {
    var body = doGet(CALL_NUMBER_STATUS_PATH, CENTRAL_TENANT_ID).andReturn().getResponse().getContentAsString();
    var items = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() { });
    return items.stream().collect(Collectors.toMap(
      item -> String.valueOf(item.get(TENANT_ID_FIELD_NAME)),
      item -> String.valueOf(item.get("status"))));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, List<Object>> callNumberTenants() {
    return Arrays.stream(fetchAllDocuments(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID))
      .collect(Collectors.toMap(hit -> String.valueOf(hit.getSourceAsMap().get(CALL_NUMBER_FIELD)),
        hit -> ((List<Map<String, Object>>) hit.getSourceAsMap().get("instances")).stream()
          .map(instance -> instance.get(TENANT_ID_FIELD_NAME)).toList()));
  }

  private static Map<String, Object> callNumberItem(String callNumber, String instanceSource) {
    var item = rawItem(instanceSource);
    item.put(EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD, mapOf(CALL_NUMBER_FIELD, callNumber));
    item.put(EFFECTIVE_LOCATION_ID_FIELD, randomId());
    return item;
  }

  private static void awaitAllCompleted() {
    awaitAssertion(() -> assertThat(statuses().values()).doesNotContain("IN_PROGRESS", "FINALIZING", "FAILED"));
    awaitAssertion(() -> assertThat(statuses()).containsValue("COMPLETED"));
  }

  @SneakyThrows
  private static Map<String, String> statuses() {
    var body = doGet(itemRecordsReindexStatusPath(), CENTRAL_TENANT_ID).andReturn().getResponse().getContentAsString();
    var items = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() { });
    return items.stream().collect(Collectors.toMap(
      item -> String.valueOf(item.get(TENANT_ID_FIELD_NAME)),
      item -> String.valueOf(item.get("status"))));
  }

  private static Map<String, Map<String, Object>> documents() {
    return Arrays.stream(fetchAllDocuments(ITEM, CENTRAL_TENANT_ID))
      .collect(Collectors.toMap(SearchHit::getId, SearchHit::getSourceAsMap));
  }

  private static Map<String, Object> rawItem(String instanceSource) {
    var item = new LinkedHashMap<String, Object>();
    item.put(ID_FIELD, randomId());
    item.put(INSTANCE_ID_FIELD, randomId());
    item.put(HOLDINGS_RECORD_ID_FIELD, randomId());
    item.put("status", mapOf("name", "Available"));
    item.put("instanceSource", instanceSource);
    return item;
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
}
