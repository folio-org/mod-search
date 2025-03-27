package org.folio.indexing;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.ITEM_TABLE;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.spring.testing.extension.DatabaseCleanup;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
@DatabaseCleanup(tenants = TENANT_ID, tables = {CALL_NUMBER_TABLE, INSTANCE_CALL_NUMBER_TABLE, ITEM_TABLE})
class IndexingInstanceCallNumberIT extends BaseIntegrationTest {

  private static final String INSTANCE_ID_1 = randomId();
  private static final String INSTANCE_ID_2 = randomId();

  @BeforeAll
  static void prepare() {
    setUpTenant();

    enableFeature(TenantConfiguredFeature.BROWSE_CALL_NUMBERS);

    var instance1 = new Instance().id(INSTANCE_ID_1).title("test");
    var instance2 = new Instance().id(INSTANCE_ID_2).title("test");
    saveRecords(TENANT_ID, instanceSearchPath(), asList(instance1, instance2), 2, emptyList(),
      instance -> inventoryApi.createInstance(TENANT_ID, instance));
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @AfterEach
  void tearDown() {
    deleteAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID);
  }

  @Test
  void shouldIndexInstanceCallNumber_createNewDocument_onItemCreate() {
    // given
    // create items with the same call number
    var item1 = getItem(randomId());
    var item2 = getItem(randomId());
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_1, item1);
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_2, item2);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID)).hasSize(1));

    // when
    // fetch all documents from search index
    var hits = fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID);

    // then
    var sourceAsMap = hits[0].getSourceAsMap();
    // assert that the document contains the expected fields
    assertThat(sourceAsMap)
      .contains(
        entry("callNumber", "NS 1 .B5"),
        entry("fullCallNumber", "NS 1 .B5"),
        entry("callNumberTypeId", "2b94c631-fca9-4892-a730-03ee529ff6c3"),
        entry("defaultShelvingOrder", "NS 1 .B5"),
        entry("deweyShelvingOrder", "NS 11 B 15"),
        entry("lcShelvingOrder", "NS 11 B5"),
        entry("sudocShelvingOrder", "!NS 11   !B 15"),
        entry("nlmShelvingOrder", "NS 11 B5")
      );

    // assert that the document contains the expected instances object with count 2
    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .hasSize(1)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID));
    @SuppressWarnings("unchecked")
    var ids = (List<String>) instances.getFirst().get("instanceId");
    assertThat(ids).containsExactlyInAnyOrder(INSTANCE_ID_1, INSTANCE_ID_2);
  }

  @Test
  void shouldIndexInstanceCallNumber_deleteDocument_onItemUpdate() {
    // given
    // create item with call number
    var item = getItem(randomId());
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_1, item);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID)).hasSize(1));

    // when update item with null call number
    item.setEffectiveCallNumberComponents(null);
    inventoryApi.updateItem(TENANT_ID, INSTANCE_ID_1, item);

    // then
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID)).isEmpty());
  }

  @Test
  void shouldIndexInstanceCallNumber_deleteDocument_onItemDelete() {
    // given
    // create item with call number
    var itemId = randomId();
    var item = getItem(itemId);
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_1, item);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID)).hasSize(1));

    // when
    inventoryApi.deleteItem(TENANT_ID, itemId);

    // then
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID)).isEmpty());
  }

  private Item getItem(String itemId) {
    return new Item().id(itemId).holdingsRecordId(randomId()).effectiveCallNumberComponents(callNumberComponents());
  }

  private ItemEffectiveCallNumberComponents callNumberComponents() {
    var callNumberTypeId = "2b94c631-fca9-4892-a730-03ee529ff6c3";
    var callNumber = "NS 1 .B5";
    return new ItemEffectiveCallNumberComponents().callNumber(callNumber).typeId(callNumberTypeId);
  }
}
