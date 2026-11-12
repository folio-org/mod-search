package org.folio.indexing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexFullPath;
import static org.folio.support.base.ApiEndpoints.itemRecordsReindexStatusPath;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
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
import lombok.SneakyThrows;
import org.folio.s3.client.FolioS3Client;
import org.folio.search.domain.dto.IndexRebuildRequest;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.api.ItemRecordsInventoryStub;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Item records reindex in the EXPORT mode: inventory exports records to a file and sends a {@code file-ready} event.
 */
@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.item-index-enabled=true",
  "folio.reindex.reindex-type=EXPORT"
})
class ItemRecordsReindexExportIT extends BaseIntegrationTest {

  private static final String SCHEMA = TENANT_ID + "_mod_search.";

  private static ItemRecordsInventoryStub inventory;

  @MockitoBean
  private FolioS3Client s3Client;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
    inventory = new ItemRecordsInventoryStub(okapi.wireMockServer(), kafkaTemplate, objectMapper, true);
  }

  @AfterAll
  static void cleanUp() {
    inventory.deactivate();
    removeTenant(TENANT_ID);
  }

  @Test
  @SneakyThrows
  void shouldRebuildItemIndexFromExportedFile() {
    when(s3Client.read(anyString()))
      .thenAnswer(invocation -> new ByteArrayInputStream(inventory.file(invocation.getArgument(0)).getBytes(UTF_8)));
    var items = List.of(rawItem("Available"), rawItem("Checked out"));
    inventory.itemCount(items.size());
    inventory.records(TENANT_ID, items);

    doPost(itemRecordsReindexFullPath(), TENANT_ID, new IndexRebuildRequest());
    awaitAssertion(() -> doGet(itemRecordsReindexStatusPath(), TENANT_ID)
      .andExpect(jsonPath("[0].status", is("COMPLETED"))));

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 2);
    for (var item : items) {
      awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + item.get("id"), TENANT_ID)
        .andExpect(jsonPath("$.items[0].statusName", is(((Map<?, ?>) item.get("status")).get("name")))));
    }
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + "item", Long.class)).isZero();
  }

  private static Map<String, Object> rawItem(String statusName) {
    var item = new LinkedHashMap<String, Object>();
    item.put("id", randomId());
    item.put("instanceId", randomId());
    item.put("holdingsRecordId", randomId());
    item.put("status", mapOf("name", statusName));
    item.put("instanceSource", "FOLIO");
    return item;
  }
}
