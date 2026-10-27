package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.indexRecordsPath;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.ItemStatus;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.item-index-enabled=true")
class IndexingItemIT extends BaseIntegrationTest {

  private static final String FAILED_EVENTS_TABLE = TENANT_ID + "_mod_search.failed_indexing_event";

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeAll
  static void prepare() {
    enableTenant(TENANT_ID);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(TENANT_ID);
  }

  @Test
  void shouldIndexUpdateAndDeleteStandaloneItem() {
    var instanceId = randomId();
    var itemId = randomId();
    var instance = new Instance().id(instanceId).title("item-index-instance");
    inventoryApi.createInstance(TENANT_ID, instance);
    awaitAssertion(() -> doSearchInstances("id==" + instanceId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1))));

    // create
    var item = new Item().id(itemId).holdingsRecordId(randomId()).barcode("bc-" + itemId)
      .status(new ItemStatus().name("Available"))
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber("NS 1 .B5"));
    inventoryApi.createItem(TENANT_ID, instanceId, item);

    awaitIndexedResourceCounts(ITEM, TENANT_ID, 1);
    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].id", is(itemId)))
      .andExpect(jsonPath("$.items[0].instanceId", is(instanceId)))
      .andExpect(jsonPath("$.items[0].statusName", is("Available")))
      .andExpect(jsonPath("$.items[0].callNumber", is("NS 1 .B5"))));

    // the instance search over nested items is not affected
    awaitAssertion(() -> doSearchInstances("items.id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1))));

    assertUpdateAndDelete(instanceId, itemId, item);
  }

  @Test
  void shouldIndexStandaloneItemFromIndexRecordsEndpoint() {
    var itemId = randomId();
    var itemEvent = resourceEvent(itemId, ITEM, CREATE,
      mapOf("id", itemId, "instanceId", randomId(), "holdingsRecordId", randomId(), "barcode", "rest-" + itemId,
        "status", mapOf("name", "Available")), null)
      .tenant(TENANT_ID);

    indexRecords(List.of(itemEvent), TENANT_ID);

    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].barcode", is("rest-" + itemId)))
      .andExpect(jsonPath("$.items[0].statusName", is("Available"))));
  }

  @Test
  void shouldStoreFailedIndexingEventInTenantSchema() throws Exception {
    var itemId = randomId();
    var itemEvent = resourceEvent(itemId, ITEM, CREATE,
      mapOf("id", itemId, "instanceId", randomId(), "holdingsRecordId", randomId(),
        "metadata", mapOf("updatedDate", "not-a-date")), null)
      .tenant(TENANT_ID);

    doPost(indexRecordsPath(), TENANT_ID, List.of(itemEvent))
      .andExpect(jsonPath("$.status", is("error")))
      .andExpect(jsonPath("$.errorMessage", containsString("Failed to index 1 of 1 item events")));

    var stored = jdbcTemplate.queryForMap(
      "SELECT resource_type, event_type, status, error_type, error_message FROM " + FAILED_EVENTS_TABLE
      + " WHERE resource_id = ?", itemId);
    assertThat(stored)
      .containsEntry("resource_type", "item")
      .containsEntry("event_type", "CREATE")
      .containsEntry("status", 400)
      .containsEntry("error_type", "mapper_parsing_exception");
    assertThat((String) stored.get("error_message")).contains("updatedDate");
    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(0))));
  }

  private void assertUpdateAndDelete(String instanceId, String itemId, Item item) {
    // update
    item.setStatus(new ItemStatus().name("Checked out"));
    inventoryApi.updateItem(TENANT_ID, instanceId, item);
    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.items[0].statusName", is("Checked out"))));

    // delete
    inventoryApi.deleteItem(TENANT_ID, itemId);
    awaitAssertion(() -> doSearch(itemSearchPath(), "id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(0))));
    awaitAssertion(() -> doSearchInstances("items.id==" + itemId, TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(0))));

    inventoryApi.deleteInstance(TENANT_ID, instanceId);
  }
}
