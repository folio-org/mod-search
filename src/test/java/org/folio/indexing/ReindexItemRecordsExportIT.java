package org.folio.indexing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsFailedPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsFullPath;
import static org.folio.support.base.ApiEndpoints.reindexItemRecordsStatus;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.domain.dto.ReindexItemRequest;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.api.ReindexInventoryStub;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;

/**
 * Items-only reindex in the EXPORT mode: inventory exports item records to a file and sends a {@code file-ready}
 * event, the lines of the file are written straight to the item index.
 */
@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.item-index-enabled=true",
  "folio.reindex.reindex-type=EXPORT"
})
class ReindexItemRecordsExportIT extends BaseIntegrationTest {

  private static final String SCHEMA = TENANT_ID + "_mod_search.";
  private static final String MERGE_COMPLETED = "MERGE_COMPLETED";
  private static final String MERGE_FAILED = "MERGE_FAILED";

  private static ReindexInventoryStub inventory;

  @MockitoBean
  private FolioS3Client s3Client;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
    inventory = new ReindexInventoryStub(okapi.wireMockServer(), kafkaTemplate, objectMapper, true);
  }

  @AfterAll
  static void cleanUp() {
    inventory.deactivate();
    removeTenant(TENANT_ID);
  }

  @BeforeEach
  @SneakyThrows
  void setUp() {
    inventory.reset();
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "reindex_status");
    jdbcTemplate.update("DELETE FROM " + SCHEMA + "merge_range");
    when(s3Client.read(anyString()))
      .thenAnswer(invocation -> new ByteArrayInputStream(inventory.file(invocation.getArgument(0)).getBytes(UTF_8)));
  }

  @Test
  void shouldRebuildItemIndexFromExportedFile() {
    var items = List.of(rawItem("Available"), rawItem("Checked out"), rawItem("Missing"));
    inventory.itemCount(items.size());
    inventory.records(TENANT_ID, items);

    doPost(reindexItemRecordsFullPath(), TENANT_ID, new ReindexItemRequest());
    awaitItemStatus(MERGE_COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 3);
    for (var item : items) {
      awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + item.get("id"), TENANT_ID)
        .andExpect(jsonPath("$.totalRecords", is(1)))
        .andExpect(jsonPath("$.items[0].statusName", is(((Map<?, ?>) item.get("status")).get("name"))))
        .andExpect(jsonPath("$.items[0].callNumber", is("NS 1 .B5"))));
    }
    assertThat(statuses()).containsOnlyKeys("ITEM");
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + "item", Long.class)).isZero();
  }

  @Test
  void shouldMarkItemFailedAndRecoverFailedRangesFromExportedFile() {
    var goodItem = rawItem("Available");
    var badItem = rawItem("Available");
    ((Map<String, Object>) badItem.get("metadata")).put("updatedDate", "not-a-date");
    inventory.itemCount(2);
    inventory.records(TENANT_ID, List.of(goodItem, badItem));

    doPost(reindexItemRecordsFullPath(), TENANT_ID, new ReindexItemRequest());
    awaitItemStatus(MERGE_FAILED);

    ((Map<String, Object>) badItem.get("metadata")).put("updatedDate", "2024-05-01T10:15:30.123+00:00");
    inventory.records(TENANT_ID, List.of(goodItem, badItem));
    doPost(reindexItemRecordsFailedPath(), TENANT_ID, Map.of());
    awaitItemStatus(MERGE_COMPLETED);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 2);
  }

  private static Map<String, Object> rawItem(String statusName) {
    var id = randomId();
    var item = new LinkedHashMap<String, Object>();
    item.put("id", id);
    item.put("instanceId", randomId());
    item.put("holdingsRecordId", randomId());
    item.put("barcode", "bc-" + id);
    item.put("status", mapOf("name", statusName));
    item.put("effectiveCallNumberComponents", mapOf("callNumber", "NS 1 .B5"));
    item.put("metadata", new LinkedHashMap<>(mapOf("createdDate", "2024-05-01T10:15:30.123+00:00",
      "updatedDate", "2024-05-01T10:15:30.123+00:00")));
    return item;
  }

  private void awaitItemStatus(String expected) {
    awaitAssertion(() -> assertThat(statuses()).containsEntry("ITEM", expected));
  }

  @SneakyThrows
  private static Map<String, String> statuses() {
    var body = doGet(reindexItemRecordsStatus(), TENANT_ID).andReturn().getResponse().getContentAsString();
    var items = objectMapper.readValue(body, new TypeReference<List<Map<String, Object>>>() { });
    return items.stream().collect(Collectors.toMap(
      item -> String.valueOf(item.get("entityType")), item -> String.valueOf(item.get("status"))));
  }
}
