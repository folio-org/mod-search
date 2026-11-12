package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.search.utils.SearchUtils.EFFECTIVE_LOCATION_ID_FIELD;
import static org.folio.search.utils.SearchUtils.HOLDINGS_RECORD_ID_FIELD;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFullPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexStatusPath;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
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

/**
 * Call number records reindex: documents are built from the item index, and kept up to date from item changes.
 */
@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.item-index-enabled=true",
  "folio.search-config.indexing.call-number-index-from-items-enabled=true",
  "folio.search-config.indexing.call-number-refresh-delay-ms=500"
})
class CallNumberRecordsReindexIT extends BaseIntegrationTest {

  private static final String SCHEMA = TENANT_ID + "_mod_search.";
  private static final String LOCATION_ID = randomId();
  private static final String FULL_PATH = "/search/index/call-number-records/reindex/full";
  private static final String STATUS_PATH = "/search/index/call-number-records/reindex/status";

  private static ItemRecordsInventoryStub inventory;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
    enableFeature(TENANT_ID, TenantConfiguredFeature.BROWSE_CALL_NUMBERS);
    inventory = new ItemRecordsInventoryStub(okapi.wireMockServer(), kafkaTemplate, objectMapper, false);
  }

  @AfterAll
  static void cleanUp() {
    inventory.deactivate();
    removeTenant(TENANT_ID);
  }

  @BeforeEach
  void setUp() {
    inventory.reset();
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_state");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_range");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_dirty_key");
  }

  @Test
  @SneakyThrows
  void shouldRejectRunUntilItemIndexIsUpToDate() {
    attemptPost(FULL_PATH, TENANT_ID, new IndexRebuildRequest()).andExpect(status().isBadRequest());
    doGet(STATUS_PATH, TENANT_ID).andExpect(jsonPath("[0].blockedBy", contains("item")));

    runItemReindex(List.of(item("A1", randomId())));
    jdbcTemplate.update("UPDATE " + SCHEMA + "index_rebuild_state SET fingerprint = 'old' WHERE resource = 'item'");

    attemptPost(FULL_PATH, TENANT_ID, new IndexRebuildRequest()).andExpect(status().isBadRequest());
  }

  @Test
  @SneakyThrows
  void shouldBuildDocumentsFromItemIndexWithoutTouchingIt() {
    var instance1 = randomId();
    var instance2 = randomId();
    runItemReindex(List.of(item("A1", instance1), item("A1", instance2), item("B2", instance1), noCallNumberItem()));
    final var itemDocuments = sources(fetchAllDocuments(ITEM, TENANT_ID));

    runCallNumberReindex();

    awaitAssertion(() -> assertThat(callNumbers()).containsOnlyKeys("A1", "B2"));
    var document = fetchDocument("A1");
    assertThat(document).containsEntry("fullCallNumber", "A1").containsKey("defaultShelvingOrder");
    assertThat(instances(document)).singleElement().satisfies(instance -> {
      assertThat(instance).containsEntry("tenantId", TENANT_ID).containsEntry("shared", false);
      assertThat(instanceIds(instance)).containsExactlyInAnyOrder(instance1, instance2);
    });
    assertThat(sources(fetchAllDocuments(ITEM, TENANT_ID))).isEqualTo(itemDocuments);
    doGet(STATUS_PATH, TENANT_ID).andExpect(jsonPath("[0].reindexRequired", is(false)));
  }

  @Test
  void shouldUpdateDocumentsFromItemChanges() {
    runItemReindex(List.of(item("A1", randomId())));
    runCallNumberReindex();
    awaitAssertion(() -> assertThat(callNumbers()).containsOnlyKeys("A1"));

    var newItem = item("C3", randomId());
    indexRecords(List.of(resourceEvent(String.valueOf(newItem.get("id")), ITEM, CREATE, newItem, null)), TENANT_ID);
    awaitAssertion(() -> assertThat(callNumbers()).containsOnlyKeys("A1", "C3"));

    indexRecords(List.of(resourceEvent(String.valueOf(newItem.get("id")), ITEM, DELETE, null, newItem)), TENANT_ID);
    awaitAssertion(() -> assertThat(callNumbers()).containsOnlyKeys("A1"));
  }

  @Test
  void shouldHoldChangesDuringRunAndApplyThemAfterwards() {
    runItemReindex(List.of(item("A1", randomId())));
    runCallNumberReindex();
    awaitAssertion(() -> assertThat(callNumbers()).containsOnlyKeys("A1"));
    jdbcTemplate.update("UPDATE " + SCHEMA + "index_rebuild_state SET status = 'IN_PROGRESS' "
      + "WHERE resource = 'call_number'");

    var newItem = item("D4", randomId());
    indexRecords(List.of(resourceEvent(String.valueOf(newItem.get("id")), ITEM, CREATE, newItem, null)), TENANT_ID);
    await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6))
      .until(() -> !callNumbers().containsKey("D4"));

    jdbcTemplate.update("UPDATE " + SCHEMA + "index_rebuild_state SET status = 'COMPLETED' "
      + "WHERE resource = 'call_number'");
    awaitAssertion(() -> assertThat(callNumbers()).containsKey("D4"));
  }

  @Test
  void shouldRejectItemRunWhileCallNumberRunIsActive() throws Exception {
    runItemReindex(List.of(item("A1", randomId())));
    jdbcTemplate.update("INSERT INTO " + SCHEMA + "index_rebuild_state (resource, tenant_id, status) "
      + "VALUES ('call_number', ?, 'IN_PROGRESS')", TENANT_ID);

    attemptPost(itemRecordsReindexFullPath(), TENANT_ID, new IndexRebuildRequest()).andExpect(status().isBadRequest());
  }

  private void runItemReindex(List<Map<String, Object>> items) {
    inventory.itemCount(items.size());
    inventory.records(TENANT_ID, items);
    doPost(itemRecordsReindexFullPath(), TENANT_ID, new IndexRebuildRequest());
    awaitAssertion(() -> doGet(itemRecordsReindexStatusPath(), TENANT_ID)
      .andExpect(jsonPath("[0].status", is("COMPLETED"))));
    awaitIndexedResourceCounts(ITEM, TENANT_ID, items.size());
  }

  private static void runCallNumberReindex() {
    doPost(FULL_PATH, TENANT_ID, new IndexRebuildRequest());
    awaitAssertion(() -> doGet(STATUS_PATH, TENANT_ID).andExpect(jsonPath("[0].status", is("COMPLETED"))));
  }

  private static Map<String, Map<String, Object>> callNumbers() {
    return Arrays.stream(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID))
      .collect(Collectors.toMap(hit -> String.valueOf(hit.getSourceAsMap().get("callNumber")),
        SearchHit::getSourceAsMap));
  }

  private static Map<String, Object> fetchDocument(String callNumber) {
    return callNumbers().get(callNumber);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> instances(Map<String, Object> document) {
    return (List<Map<String, Object>>) document.get("instances");
  }

  @SuppressWarnings("unchecked")
  private static List<String> instanceIds(Map<String, Object> instance) {
    return (List<String>) instance.get("instanceId");
  }

  private static List<String> sources(SearchHit[] hits) {
    return Arrays.stream(hits).map(hit -> hit.getId() + hit.getSourceAsString()).sorted().toList();
  }

  private static Map<String, Object> item(String callNumber, String instanceId) {
    var item = noCallNumberItem();
    item.put("instanceId", instanceId);
    item.put("effectiveCallNumberComponents", mapOf("callNumber", callNumber));
    return item;
  }

  private static Map<String, Object> noCallNumberItem() {
    var item = new LinkedHashMap<String, Object>();
    item.put(ID_FIELD, randomId());
    item.put(INSTANCE_ID_FIELD, randomId());
    item.put(HOLDINGS_RECORD_ID_FIELD, randomId());
    item.put(EFFECTIVE_LOCATION_ID_FIELD, LOCATION_ID);
    item.put("status", mapOf("name", "Available"));
    item.put("instanceSource", "FOLIO");
    return item;
  }
}
