package org.folio.search.controller;

import static org.folio.search.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;

import java.util.List;
import java.util.stream.IntStream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class IndexingInstanceIT extends BaseIntegrationTest {

  private static final List<String> INSTANCE_IDS = getRandomIds(3);
  private static final List<String> ITEM_IDS = getRandomIds(2);
  private static final List<String> HOLDING_IDS = getRandomIds(4);

  @BeforeAll
  static void prepare() {
    setUpTenant();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant();
  }

  @Test
  void shouldRemoveItem() {
    createInstances();
    var itemIdToDelete = ITEM_IDS.get(1);
    inventoryApi.deleteItem(TENANT_ID, itemIdToDelete);
    assertCountByQuery(instanceSearchPath(), "items.id=={value}", itemIdToDelete, 0);
    assertCountByQuery(instanceSearchPath(), "items.id=={value}", ITEM_IDS.get(0), 1);
  }

  @Test
  void shouldUpdateAndDeleteInstance() {
    var instanceId = randomId();
    var instance = new Instance().id(instanceId).title("test-resource");

    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByQuery(instanceSearchPath(), "title=={value}", "test-resource", 1);

    var instanceToUpdate = new Instance().id(instanceId).title("test-resource-updated");
    inventoryApi.updateInstance(TENANT_ID, instanceToUpdate);
    assertCountByQuery(instanceSearchPath(), "title=={value}", "test-resource-updated", 1);

    inventoryApi.deleteInstance(TENANT_ID, instanceId);
    assertCountByQuery(instanceSearchPath(), "id=={value}", instanceId, 0);
  }

  @Test
  void shouldUpdateBoundWith() {
    var instanceId = randomId();
    var instance = new Instance().id(instanceId).title("test-resource");

    inventoryApi.createInstance(TENANT_ID, instance);
    assertCountByQuery(instanceSearchPath(), "title=={value}", "test-resource", 1);
    assertCountByQuery(instanceSearchPath(), "isBoundWith=={value}", "false", 1);

    inventoryApi.createBoundWith(TENANT_ID, instanceId);
    assertCountByQuery(instanceSearchPath(), "isBoundWith=={value}", "true", 1);
  }

  @Test
  void shouldRemoveHolding() {
    createInstances();
    HOLDING_IDS.forEach(id -> assertCountByQuery(instanceSearchPath(), "holdings.id=={value}", id, 1));
    inventoryApi.deleteHolding(TENANT_ID, HOLDING_IDS.get(0));
    assertCountByIds(instanceSearchPath(), List.of(HOLDING_IDS.get(0)), 0);
    HOLDING_IDS.subList(1, 4)
      .forEach(id -> assertCountByQuery(instanceSearchPath(), "holdings.id=={value}", id, 1));
  }

  @Test
  void shouldRemoveInstance() {
    createInstances();
    var instanceIdToDelete = INSTANCE_IDS.get(0);

    assertCountByIds(instanceSearchPath(), INSTANCE_IDS, INSTANCE_IDS.size());

    inventoryApi.deleteInstance(TENANT_ID, instanceIdToDelete);
    assertCountByIds(instanceSearchPath(), List.of(instanceIdToDelete), 0);
    List<String> ids = INSTANCE_IDS.subList(1, 3);
    assertCountByIds(instanceSearchPath(), ids, 2);
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
    assertCountByIds(instanceSearchPath(), INSTANCE_IDS, 3);
    for (String itemId : ITEM_IDS) {
      assertCountByQuery(instanceSearchPath(), "items.id=={value}", itemId, 1);
    }
    for (String holdingId : HOLDING_IDS) {
      assertCountByQuery(instanceSearchPath(), "holdings.id=={value}", holdingId, 1);
    }
  }

}
