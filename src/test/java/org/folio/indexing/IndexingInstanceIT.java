package org.folio.indexing;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.List;
import java.util.stream.IntStream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;

public abstract class IndexingInstanceIT extends BaseSharedTest {

  private static final List<String> INSTANCE_IDS = getRandomIds(3);
  private static final List<String> ITEM_IDS = getRandomIds(2);
  private static final List<String> HOLDING_IDS = getRandomIds(4);

  @Test
  void shouldRemoveItem() {
    createInstances();
    var itemIdToDelete = ITEM_IDS.get(1);
    inventoryApi.deleteItem(TENANT_ID, itemIdToDelete);
    assertSearchByQueryCount(instanceSearchPath(), "items.id=={value}", itemIdToDelete, 0, TENANT_ID);
    assertSearchByQueryCount(instanceSearchPath(), "items.id=={value}", ITEM_IDS.getFirst(), 1, TENANT_ID);
  }

  @Test
  void shouldUpdateAndDeleteInstance() {
    var instanceId = randomId();
    var instance = new Instance().id(instanceId).title("test-resource");

    inventoryApi.createInstance(TENANT_ID, instance);
    assertSearchByQueryCount(instanceSearchPath(), "title=={value}", "test-resource", 1, TENANT_ID);

    var instanceToUpdate = new Instance().id(instanceId).title("test-resource-updated");
    inventoryApi.updateInstance(TENANT_ID, instanceToUpdate);
    assertSearchByQueryCount(instanceSearchPath(), "title=={value}", "test-resource-updated", 1, TENANT_ID);

    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    assertSearchByQueryCount(instanceSearchPath(), "id=={value}", instanceId, 0, TENANT_ID);
  }

  @Test
  void shouldIndexInstanceThatHasFieldWithMoreThen32000Characters() {
    var instanceId = randomId();
    var instance = new Instance().id(instanceId).addAdministrativeNotesItem("🙂".repeat(32001));

    inventoryApi.createInstance(TENANT_ID, instance);
    assertSearchByQueryCount(instanceSearchPath(), "id==\"{value}\"", instanceId, 1, TENANT_ID);
  }

  @Test
  void shouldUpdateBoundWith() {
    var instanceId = randomId();
    var instance = new Instance().id(instanceId).title("test-resource");

    inventoryApi.createInstance(TENANT_ID, instance);
    assertSearchByQueryCount(instanceSearchPath(), "title=={value}", "test-resource", 1, TENANT_ID);
    assertSearchByQueryCount(instanceSearchPath(), "isBoundWith=={value}", "false", 1, TENANT_ID);

    inventoryApi.createBoundWith(TENANT_ID, instanceId);
    assertSearchByQueryCount(instanceSearchPath(), "isBoundWith=={value}", "true", 1, TENANT_ID);
  }

  @Test
  void shouldRemoveHolding() {
    createInstances();
    HOLDING_IDS.forEach(id -> assertSearchByQueryCount(instanceSearchPath(), "holdings.id=={value}", id, 1, TENANT_ID));
    inventoryApi.deleteHolding(TENANT_ID, HOLDING_IDS.getFirst());
    assertSearchByIdsCount(instanceSearchPath(), List.of(HOLDING_IDS.getFirst()), 0, TENANT_ID);
    HOLDING_IDS.subList(1, 4)
      .forEach(id -> assertSearchByQueryCount(instanceSearchPath(), "holdings.id=={value}", id, 1, TENANT_ID));
  }

  @Test
  void shouldRemoveInstance() {
    createInstances();
    var instanceIdToDelete = INSTANCE_IDS.getFirst();

    assertSearchByIdsCount(instanceSearchPath(), INSTANCE_IDS, INSTANCE_IDS.size(), TENANT_ID);

    inventoryApi.deleteInstance(TENANT_ID, instanceIdToDelete);
    assertSearchByIdsCount(instanceSearchPath(), List.of(instanceIdToDelete), 0, TENANT_ID);
    List<String> ids = INSTANCE_IDS.subList(1, 3);
    assertSearchByIdsCount(instanceSearchPath(), ids, 2, TENANT_ID);
  }

  private static Item item(int i) {
    return new Item().id(ITEM_IDS.get(i)).holdingsRecordId(HOLDING_IDS.get(i));
  }

  private static Holding holdingsRecord(int i) {
    return new Holding().id(HOLDING_IDS.get(i));
  }

  private static List<String> getRandomIds(int count) {
    return IntStream.range(0, count).mapToObj(index -> randomId()).toList();
  }

  private void createInstances() {
    var instances = INSTANCE_IDS.stream()
      .map(id -> new Instance().id(id).title("title-" + id))
      .toList();

    instances.get(0)
      .holdings(List.of(holdingsRecord(0), holdingsRecord(1)))
      .items(List.of(item(0), item(1)));

    instances.get(1).holdings(List.of(holdingsRecord(2), holdingsRecord(3)));

    instances.forEach(instance -> inventoryApi.createInstance(TENANT_ID, instance));
    assertSearchByIdsCount(instanceSearchPath(), INSTANCE_IDS, 3, TENANT_ID);
    for (String itemId : ITEM_IDS) {
      assertSearchByQueryCount(instanceSearchPath(), "items.id=={value}", itemId, 1, TENANT_ID);
    }
    for (String holdingId : HOLDING_IDS) {
      assertSearchByQueryCount(instanceSearchPath(), "holdings.id=={value}", holdingId, 1, TENANT_ID);
    }
  }
}
