package org.folio.indexing;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForClassTypes.entry;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public abstract class IndexingInstanceCallNumberIT extends BaseSharedTest {

  private static final String INSTANCE_ID_1 = randomId();
  private static final String INSTANCE_ID_2 = randomId();
  private static final String LOCATION_ID = randomId();

  @BeforeEach
  void prepare() {
    var instance1 = new Instance().id(INSTANCE_ID_1).title("test1");
    var instance2 = new Instance().id(INSTANCE_ID_2).title("test2");
    saveRecords(TENANT_ID, instanceSearchPath(), asList(instance1, instance2), 2, emptyList(),
      instance -> inventoryApi.createInstance(TENANT_ID, instance));
  }

  @Test
  void shouldIndexInstanceCallNumber_createNewDocument_onItemCreate() {
    // given
    // create items with the same call number
    var item1 = getItem(randomId());
    var item2 = getItem(randomId());
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_1, item1);
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_2, item2);

    awaitAssertion(this::assertCallNumberDocumentIndexed);

    inventoryApi.deleteItem(TENANT_ID, item1.getId());
    inventoryApi.deleteItem(TENANT_ID, item2.getId());
  }

  @Test
  void shouldIndexInstanceCallNumber_deleteDocument_onItemUpdate() {
    // given
    // create item with call number
    var item = getItem(randomId());
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_1, item);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID))
      .as("Should have exactly 1 call number document before update")
      .hasSize(1));

    // when update item with null call number
    item.setEffectiveCallNumberComponents(null);
    inventoryApi.updateItem(TENANT_ID, INSTANCE_ID_1, item);

    // then
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID))
      .as("Call number document should be removed after item call number is cleared")
      .isEmpty());
  }

  @Test
  void shouldIndexInstanceCallNumber_deleteDocument_onItemDelete() {
    // given
    // create item with call number
    var itemId = randomId();
    var item = getItem(itemId);
    inventoryApi.createItem(TENANT_ID, INSTANCE_ID_1, item);
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID))
      .as("Should have exactly 1 call number document before delete")
      .hasSize(1));

    // when
    inventoryApi.deleteItem(TENANT_ID, itemId);

    // then
    awaitAssertion(() -> assertThat(fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID))
      .as("Call number document should be removed after item delete")
      .isEmpty());
  }

  @SuppressWarnings("unchecked")
  private void assertCallNumberDocumentIndexed() {
    var hits = fetchAllDocuments(INSTANCE_CALL_NUMBER, TENANT_ID);
    assertThat(hits).as("Should have exactly 1 call number document in the index").hasSize(1);
    var sourceAsMap = hits[0].getSourceAsMap();
    assertCallNumberDocFields(sourceAsMap);
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .as("Instances list should contain exactly 1 shared/tenant group")
      .hasSize(1)
      .allSatisfy(map -> assertThat(map).containsEntry("shared", false))
      .allSatisfy(map -> assertThat(map).containsEntry("tenantId", TENANT_ID));
    var ids = (List<String>) instances.getFirst().get("instanceId");
    assertThat(ids)
      .as("Instance IDs should contain both indexed instances")
      .containsExactlyInAnyOrder(INSTANCE_ID_1, INSTANCE_ID_2);
  }

  private void assertCallNumberDocFields(Map<String, Object> sourceAsMap) {
    assertThat(sourceAsMap)
      .as("Call number document should contain expected indexed fields")
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
  }

  private Item getItem(String itemId) {
    return new Item().id(itemId).holdingsRecordId(randomId()).effectiveLocationId(LOCATION_ID)
      .effectiveCallNumberComponents(callNumberComponents());
  }

  private ItemEffectiveCallNumberComponents callNumberComponents() {
    var callNumberTypeId = "2b94c631-fca9-4892-a730-03ee529ff6c3";
    var callNumber = "NS 1 .B5";
    return new ItemEffectiveCallNumberComponents().callNumber(callNumber).typeId(callNumberTypeId);
  }
}
