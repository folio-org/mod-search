package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.base.ApiEndpoints.reindexInstanceRecordsStatus;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsFailedPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsFullPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsStatus;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.is;
import static org.opensearch.index.query.QueryBuilders.rangeQuery;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ReindexItemRequest;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.SubResourcesLockRepository;
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
 * Items-only reindex in the PUBLISH mode: item records come from inventory as {@code reindex-records} events and are
 * written straight to the item index.
 */
@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.item-index-enabled=true")
class ReindexItemRecordsIT extends BaseIntegrationTest {

  private static final String SCHEMA = TENANT_ID + "_mod_search.";
  private static final String MERGE_COMPLETED = "MERGE_COMPLETED";
  private static final String MERGE_FAILED = "MERGE_FAILED";
  private static final String MERGE_IN_PROGRESS = "MERGE_IN_PROGRESS";

  private static ReindexInventoryStub inventory;

  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private SubResourcesLockRepository lockRepository;

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
    inventory = new ReindexInventoryStub(okapi.wireMockServer(), kafkaTemplate, objectMapper, false);
  }

  @AfterAll
  static void cleanUp() {
    inventory.deactivate();
    removeTenant(TENANT_ID);
  }

  @BeforeEach
  void setUp() {
    inventory.reset();
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "reindex_status");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "merge_range");
    deleteAllDocuments(ITEM, TENANT_ID);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 0);
  }

  @Test
  void shouldRebuildItemIndexFromInventoryWithoutTouchingOtherData() throws Exception {
    var instanceId = randomId();
    final var eventItemId = createInstanceWithEventIndexedItem(instanceId);
    final var before = takeSnapshot();

    var items = List.of(rawItem(instanceId, "Available"), rawItem(instanceId, "Checked out"),
      rawItem(instanceId, "In transit"));
    inventory.itemCount(items.size());
    inventory.records(TENANT_ID, items);
    startItemReindex();
    awaitItemStatus(MERGE_COMPLETED);
    assertItemRecordsStatusCompleted();

    // AC1 and AC4: one document per item, searchable through the item search
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 3);
    assertThat(documentIds(ITEM)).containsExactlyInAnyOrderElementsOf(
      items.stream().map(item -> item.get("id") + "|" + TENANT_ID).toList());
    for (var item : items) {
      assertItemSearchable(item, instanceId);
    }
    // the index was recreated, so the item written by an event before the run is gone
    doSearch(itemSearchPath(), "id==" + eventItemId, TENANT_ID).andExpect(jsonPath("$.totalRecords", is(0)));

    // AC2: nothing but the item index and the ITEM status row is touched
    assertThat(takeSnapshot()).isEqualTo(before);
    assertThat(statuses()).isEmpty();
    assertThat(jdbcTemplate.queryForList("SELECT DISTINCT entity_type FROM " + SCHEMA + "merge_range", String.class))
      .containsExactly("item");
    // the sub-resource lock is not left held
    withLockedSubResource(lockRepository, ReindexEntityType.ITEM, TENANT_ID, () -> { });
  }

  @Test
  void shouldIndexSameDocumentAsEventIndexing() {
    var eventRecord = rawItem(randomId(), "Available");
    var reindexRecord = new LinkedHashMap<>(eventRecord);
    reindexRecord.put("id", randomId());

    indexRecords(List.of(resourceEvent(String.valueOf(eventRecord.get("id")), ITEM, CREATE, eventRecord, null)),
      TENANT_ID);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    final var eventDocument = withoutId(fetchAllDocuments(ITEM, TENANT_ID)[0]);

    inventory.itemCount(1);
    inventory.records(TENANT_ID, List.of(reindexRecord));
    startItemReindex();
    awaitItemStatus(MERGE_COMPLETED);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    var reindexDocument = withoutId(fetchAllDocuments(ITEM, TENANT_ID)[0]);

    // AC3: reindexed and event-indexed documents have the same fields, the same dates and the same tenant data
    assertThat(reindexDocument).isEqualTo(eventDocument);
    assertThat(reindexDocument).containsEntry("tenantId", TENANT_ID).containsKey("metadata");
  }

  @Test
  void shouldAcceptDateFormatsOfInventoryRecords() {
    // inventory events are serialized from the Item POJO, reindex records are the raw jsonb. Whatever form a date
    // takes there, the field must be accepted and compare as the same instant.
    var dates = List.<Object>of("2024-05-01T10:15:30.123+00:00", "2024-05-01T10:15:30Z",
      "2024-05-01T10:15:30.123456+00:00", 1714558530123L);
    var records = new ArrayList<Map<String, Object>>();
    dates.forEach(date -> {
      var record = rawItem(randomId(), "Available");
      ((Map<String, Object>) record.get("metadata")).put("updatedDate", date);
      records.add(record);
    });
    inventory.itemCount(records.size());
    inventory.records(TENANT_ID, records);

    startItemReindex();
    awaitItemStatus(MERGE_COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, records.size());
    awaitIndexedResourceCounts(
      rangeQuery("metadata.updatedDate").gte("2024-05-01T10:15:30Z").lte("2024-05-01T10:15:31Z"),
      ITEM, TENANT_ID, records.size());
  }

  @Test
  void shouldNotOverwriteItemIndexedByEventDuringRun() throws Exception {
    var instanceId = randomId();
    var eventItem = rawItem(instanceId, "Checked out");
    var otherItem = rawItem(instanceId, "Available");
    var staleRecord = new LinkedHashMap<>(eventItem);
    staleRecord.put("status", mapOf("name", "Available"));
    inventory.itemCount(2);
    inventory.records(TENANT_ID, List.of(staleRecord, otherItem));
    inventory.holdRanges(true);

    startItemReindex();
    awaitAssertion(() -> assertThat(inventory.heldRanges()).hasSize(1));
    indexRecords(List.of(resourceEvent(String.valueOf(eventItem.get("id")), ITEM, CREATE, eventItem, null)),
      TENANT_ID);
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    inventory.releaseHeldRanges();
    awaitItemStatus(MERGE_COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 2);
    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + eventItem.get("id"), TENANT_ID)
      .andExpect(jsonPath("$.items[0].statusName", is("Checked out"))));
    doSearch(itemSearchPath(), "id==" + otherItem.get("id"), TENANT_ID)
      .andExpect(jsonPath("$.items[0].statusName", is("Available")));
  }

  @Test
  void shouldMarkOnlyItemFailedAndRecoverFailedRanges() {
    insertStatus("INSTANCE", "UPLOAD_COMPLETED");
    insertStatus("HOLDINGS", "MERGE_COMPLETED");
    var instanceId = randomId();
    var goodItem = rawItem(instanceId, "Available");
    var badItem = rawItem(instanceId, "Available");
    ((Map<String, Object>) badItem.get("metadata")).put("updatedDate", "not-a-date");
    inventory.itemCount(2);
    inventory.records(TENANT_ID, List.of(goodItem, badItem));

    startItemReindex();
    awaitItemStatus(MERGE_FAILED);

    // AC5: only the ITEM row failed, the failure is stored on the range
    assertThat(statuses()).containsOnly(Map.entry("INSTANCE", "UPLOAD_COMPLETED"),
      Map.entry("HOLDINGS", MERGE_COMPLETED));
    assertThat(jdbcTemplate.queryForList(
      "SELECT status || ': ' || fail_cause FROM " + SCHEMA + "merge_range WHERE entity_type = 'item'", String.class))
      .singleElement().asString().startsWith("FAIL").contains("mapper_parsing_exception");

    // the failed range is republished and now delivers a valid record
    ((Map<String, Object>) badItem.get("metadata")).put("updatedDate", "2024-05-01T10:15:30.123+00:00");
    inventory.records(TENANT_ID, List.of(goodItem, badItem));
    doPost(reindexItemRecordsFailedPath(), TENANT_ID, Map.of());
    awaitItemStatus(MERGE_COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 2);
    assertThat(statuses()).containsEntry("INSTANCE", "UPLOAD_COMPLETED").containsEntry("HOLDINGS", MERGE_COMPLETED);
    assertThat(inventory.publishedRanges()).hasSize(2);
  }

  @Test
  void shouldCompleteAndCreateEmptyIndexWhenInventoryHasNoItems() {
    inventory.itemCount(0);
    startItemReindex();
    awaitItemStatus(MERGE_COMPLETED);

    assertThat(countIndexDocument(ITEM, TENANT_ID)).isZero();
    assertThat(itemStatuses()).containsOnlyKeys("ITEM");
  }

  @Test
  void shouldRejectItemReindexWhenAnotherReindexIsInProgress() throws Exception {
    insertStatus("INSTANCE", MERGE_IN_PROGRESS);

    attemptPost(reindexItemRecordsFullPath(), TENANT_ID, new ReindexItemRequest())
      .andExpect(status().isBadRequest());

    assertThat(statuses()).containsOnlyKeys("INSTANCE");
    assertThat(inventory.publishedRanges()).isEmpty();
  }

  private void assertItemSearchable(Map<String, Object> item, String instanceId) {
    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + item.get("id"), TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].instanceId", is(instanceId)))
      .andExpect(jsonPath("$.items[0].barcode", is(item.get("barcode"))))
      .andExpect(jsonPath("$.items[0].statusName", is(((Map<?, ?>) item.get("status")).get("name"))))
      .andExpect(jsonPath("$.items[0].callNumber", is("NS 1 .B5"))));
  }

  private Snapshot takeSnapshot() {
    return new Snapshot(documentSources(INSTANCE), itemTable(),
      jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + "call_number", Long.class));
  }

  private String createInstanceWithEventIndexedItem(String instanceId) {
    var itemId = randomId();
    inventoryApi.createInstance(TENANT_ID, new Instance().id(instanceId).title("item-reindex-" + instanceId));
    awaitAssertion(() -> doSearchInstances("id==" + instanceId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1))));
    inventoryApi.createItem(TENANT_ID, instanceId, new Item().id(itemId).holdingsRecordId(randomId()));
    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    awaitAssertion(() -> doSearchInstances("items.id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1))));
    await().atMost(TEN_SECONDS).pollInterval(ONE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + "item WHERE id = ?::uuid",
        Long.class, itemId)).isEqualTo(1));
    return itemId;
  }

  private static Map<String, Object> rawItem(String instanceId, String statusName) {
    var id = randomId();
    var item = new LinkedHashMap<String, Object>();
    item.put("id", id);
    item.put("instanceId", instanceId);
    item.put("holdingsRecordId", randomId());
    item.put("hrid", "it" + id.substring(0, 8));
    item.put("barcode", "bc-" + id);
    item.put("status", mapOf("name", statusName, "date", "2024-05-01T10:15:30.123+00:00"));
    item.put("effectiveCallNumberComponents", mapOf("callNumber", "NS 1 .B5"));
    item.put("discoverySuppress", false);
    item.put("metadata", new LinkedHashMap<>(mapOf("createdDate", "2024-05-01T10:15:30.123+00:00",
      "createdByUserId", randomId(), "updatedDate", "2024-05-01T10:15:30.123+00:00", "updatedByUserId", randomId())));
    return item;
  }

  private static void startItemReindex() {
    doPost(reindexItemRecordsFullPath(), TENANT_ID, new ReindexItemRequest());
  }

  private void insertStatus(String entityType, String status) {
    jdbcTemplate.update("INSERT INTO " + SCHEMA + "reindex_status (entity_type, status, total_merge_ranges, "
      + "processed_merge_ranges, total_upload_ranges, processed_upload_ranges) VALUES (?, ?, 1, 1, 1, 1)",
      entityType, status);
  }

  @SneakyThrows
  private static void assertItemRecordsStatusCompleted() {
    doGet(reindexItemRecordsStatus(), TENANT_ID)
      .andExpect(jsonPath("$.length()", is(1)))
      .andExpect(jsonPath("[0].entityType", is("ITEM")))
      .andExpect(jsonPath("[0].status", is(MERGE_COMPLETED)));
  }

  private void awaitItemStatus(String expected) {
    awaitAssertion(() -> assertThat(itemStatuses()).containsEntry("ITEM", expected));
  }

  private static Map<String, String> itemStatuses() {
    return statuses(reindexItemRecordsStatus());
  }

  private static Map<String, String> statuses() {
    return statuses(reindexInstanceRecordsStatus());
  }

  @SneakyThrows
  private static Map<String, String> statuses(String path) {
    var body = doGet(path, TENANT_ID).andReturn().getResponse().getContentAsString();
    var items = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() { });
    return items.stream().collect(Collectors.toMap(
      item -> String.valueOf(item.get("entityType")), item -> String.valueOf(item.get("status"))));
  }

  private List<String> itemTable() {
    return jdbcTemplate.queryForList("SELECT id || ' ' || last_updated_date || ' ' || coalesce(instance_id::text, '')"
      + " FROM " + SCHEMA + "item ORDER BY id", String.class);
  }

  private static List<String> documentIds(ResourceType resourceType) {
    return Arrays.stream(fetchAllDocuments(resourceType, TENANT_ID)).map(SearchHit::getId).toList();
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

  private record Snapshot(List<String> instanceDocuments, List<String> itemTable, Long callNumbers) { }
}
