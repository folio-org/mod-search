package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFailedPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFullPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexStatusPath;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.IndexRebuildRequest;
import org.folio.search.domain.dto.Instance;
import org.folio.search.model.types.ResourceType;
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
 * Item records reindex in the PUBLISH mode: records come from inventory and are written straight to the item index.
 */
@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.item-index-enabled=true")
class ItemRecordsReindexIT extends BaseIntegrationTest {

  private static final String SCHEMA = TENANT_ID + "_mod_search.";
  private static final String DATE = "2024-05-01T10:15:30.123+00:00";
  private static final String COMPLETED = "COMPLETED";

  private static ItemRecordsInventoryStub inventory;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
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
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "index_rebuild_deleted_record");
  }

  @Test
  void shouldRebuildItemIndexWithoutTouchingOtherData() {
    var instanceId = createInstance();
    final var instances = documentSources(INSTANCE);
    final var itemRows = countRows("item");
    var items = List.of(rawItem(instanceId, "Available"), rawItem(instanceId, "Checked out"));
    runReindex(items);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 2);
    assertThat(Arrays.stream(fetchAllDocuments(ITEM, TENANT_ID)).map(SearchHit::getId))
      .containsExactlyInAnyOrder(items.get(0).get("id") + "|" + TENANT_ID, items.get(1).get("id") + "|" + TENANT_ID);
    for (var item : items) {
      awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + item.get("id"), TENANT_ID)
        .andExpect(jsonPath("$.totalRecords", is(1)))
        .andExpect(jsonPath("$.items[0].instanceId", is(instanceId)))
        .andExpect(jsonPath("$.items[0].callNumber", is("NS 1 .B5"))));
    }
    assertThat(documentSources(INSTANCE)).isEqualTo(instances);
    assertThat(countRows("item")).isEqualTo(itemRows);
    assertThat(countRows("reindex_status")).isZero();
    assertThat(countRows("merge_range")).isZero();
  }

  @Test
  void shouldIndexSameDocumentAsEventIndexing() {
    var eventRecord = rawItem(randomId(), "Available");
    var reindexRecord = new LinkedHashMap<>(eventRecord);
    reindexRecord.put("id", randomId());
    deleteAllDocuments(ITEM, TENANT_ID);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 0);
    indexEvent(eventRecord);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    final var eventDocument = withoutId(fetchAllDocuments(ITEM, TENANT_ID)[0]);

    runReindex(List.of(reindexRecord));

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    var reindexDocument = withoutId(fetchAllDocuments(ITEM, TENANT_ID)[0]);
    assertThat(reindexDocument).isEqualTo(eventDocument).doesNotContainKey("instanceSource");
  }

  @Test
  void shouldNotOverwriteItemIndexedByEventDuringRun() {
    var eventItem = rawItem(randomId(), "Checked out");
    var staleRecord = new LinkedHashMap<>(eventItem);
    staleRecord.put("status", mapOf("name", "Available"));
    startWithHeldRanges(List.of(staleRecord));

    indexEvent(eventItem);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    inventory.releaseHeldRanges();
    awaitStatus(COMPLETED);

    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + eventItem.get("id"), TENANT_ID)
      .andExpect(jsonPath("$.items[0].statusName", is("Checked out"))));
  }

  @Test
  void shouldKeepItemDeletedDuringRunDeleted() {
    var deletedItem = rawItem(randomId(), "Available");
    var otherItem = rawItem(randomId(), "Available");
    startWithHeldRanges(List.of(deletedItem, otherItem));

    var id = String.valueOf(deletedItem.get("id"));
    indexRecords(List.of(resourceEvent(id, ITEM, DELETE, null, deletedItem)), TENANT_ID);
    awaitAssertion(() -> assertThat(countRows("index_rebuild_deleted_record")).isEqualTo(1));
    inventory.releaseHeldRanges();
    awaitStatus(COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    assertThat(fetchAllDocuments(ITEM, TENANT_ID)[0].getId()).isEqualTo(otherItem.get("id") + "|" + TENANT_ID);
    assertThat(countRows("index_rebuild_deleted_record")).isZero();
  }

  @Test
  @SneakyThrows
  @SuppressWarnings("unchecked")
  void shouldReportFailureAndRecoverOnRetry() {
    var item = rawItem(randomId(), "Available");
    ((Map<String, Object>) item.get("metadata")).put("updatedDate", "not-a-date");
    inventory.itemCount(1);
    inventory.records(TENANT_ID, List.of(item));
    startReindex();
    awaitStatus("FAILED");
    doGet(itemRecordsReindexStatusPath(), TENANT_ID)
      .andExpect(jsonPath("[0].failCause", containsString("mapper_parsing_exception")))
      .andExpect(jsonPath("[0].reindexRequired", is(true)));

    var fixedItem = rawItem(randomId(), "Available");
    inventory.records(TENANT_ID, List.of(fixedItem));
    doPost(itemRecordsReindexFailedPath(), TENANT_ID, Map.of());
    awaitStatus(COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    assertThat(inventory.publishedRanges()).hasSize(2);
  }

  @Test
  void shouldIgnoreRecordsOfAbandonedRunAfterRestart() {
    var staleItem = rawItem(randomId(), "Available");
    startWithHeldRanges(List.of(staleItem));
    jdbcTemplate.update("UPDATE " + SCHEMA + "index_rebuild_state SET status = 'FAILED'");

    var freshItem = rawItem(randomId(), "Available");
    inventory.records(TENANT_ID, List.of(freshItem));
    startReindex();
    awaitAssertion(() -> assertThat(inventory.heldRanges()).hasSize(2));
    inventory.releaseHeldRanges();
    awaitStatus(COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    assertThat(fetchAllDocuments(ITEM, TENANT_ID)[0].getId()).isEqualTo(freshItem.get("id") + "|" + TENANT_ID);
  }

  @Test
  void shouldCompleteWhenInventoryHasNoItems() {
    runReindex(List.of());

    assertThat(countIndexDocument(ITEM, TENANT_ID)).isZero();
  }

  @Test
  void shouldRejectSecondRunWhileActive() throws Exception {
    startWithHeldRanges(List.of(rawItem(randomId(), "Available")));

    attemptPost(itemRecordsReindexFullPath(), TENANT_ID, new IndexRebuildRequest())
      .andExpect(status().isBadRequest());

    inventory.releaseHeldRanges();
    awaitStatus(COMPLETED);
  }

  @Test
  @SneakyThrows
  void shouldReportWhetherReindexIsRequired() {
    doGet(itemRecordsReindexStatusPath(), TENANT_ID)
      .andExpect(jsonPath("[0].status", is("NOT_STARTED")))
      .andExpect(jsonPath("[0].reindexRequired", is(true)));

    runReindex(List.of(rawItem(randomId(), "Available")));
    doGet(itemRecordsReindexStatusPath(), TENANT_ID)
      .andExpect(jsonPath("[0].reindexRequired", is(false)))
      .andExpect(jsonPath("[0].totalRanges", is(1)))
      .andExpect(jsonPath("[0].processedRanges", is(1)));

    jdbcTemplate.update("UPDATE " + SCHEMA + "index_rebuild_state SET fingerprint = 'old'");
    doGet(itemRecordsReindexStatusPath(), TENANT_ID).andExpect(jsonPath("[0].reindexRequired", is(true)));
  }

  private void runReindex(List<Map<String, Object>> items) {
    inventory.itemCount(items.size());
    inventory.records(TENANT_ID, items);
    startReindex();
    awaitStatus(COMPLETED);
  }

  private void startWithHeldRanges(List<Map<String, Object>> items) {
    inventory.itemCount(items.size());
    inventory.records(TENANT_ID, items);
    inventory.holdRanges(true);
    startReindex();
    awaitAssertion(() -> assertThat(inventory.heldRanges()).hasSize(1));
  }

  private static void startReindex() {
    doPost(itemRecordsReindexFullPath(), TENANT_ID, new IndexRebuildRequest());
  }

  @SneakyThrows
  private static void awaitStatus(String expected) {
    awaitAssertion(() -> doGet(itemRecordsReindexStatusPath(), TENANT_ID)
      .andExpect(jsonPath("[0].status", is(expected))));
  }

  private static void indexEvent(Map<String, Object> item) {
    indexRecords(List.of(resourceEvent(String.valueOf(item.get("id")), ITEM, CREATE, item, null)), TENANT_ID);
  }

  private long countRows(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + table, Long.class);
  }

  private static String createInstance() {
    var instanceId = randomId();
    inventoryApi.createInstance(TENANT_ID, new Instance().id(instanceId).title("item-reindex-" + instanceId));
    awaitAssertion(() -> doSearchInstances("id==" + instanceId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1))));
    return instanceId;
  }

  private static List<String> documentSources(ResourceType resourceType) {
    return Arrays.stream(fetchAllDocuments(resourceType, TENANT_ID))
      .map(hit -> hit.getId() + hit.getSourceAsString()).sorted().toList();
  }

  private static Map<String, Object> withoutId(SearchHit hit) {
    var source = new LinkedHashMap<>(hit.getSourceAsMap());
    source.remove("id");
    return source;
  }

  private static Map<String, Object> rawItem(String instanceId, String statusName) {
    var id = randomId();
    var item = new LinkedHashMap<String, Object>();
    item.put("id", id);
    item.put("instanceId", instanceId);
    item.put("holdingsRecordId", randomId());
    item.put("hrid", "it" + id.substring(0, 8));
    item.put("barcode", "bc-" + id);
    item.put("status", mapOf("name", statusName, "date", DATE));
    item.put("effectiveCallNumberComponents", mapOf("callNumber", "NS 1 .B5"));
    item.put("discoverySuppress", false);
    item.put("metadata", new LinkedHashMap<>(mapOf("createdDate", DATE, "createdByUserId", randomId(),
      "updatedDate", DATE, "updatedByUserId", randomId())));
    item.put("instanceSource", "FOLIO");
    return item;
  }
}
