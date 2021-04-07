package org.folio.search.controller;

import static org.awaitility.Awaitility.await;
import static org.folio.search.support.base.ApiEndpoints.searchInstancesByQuery;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.Test;

@IntegrationTest
class IndexingIT extends BaseIntegrationTest {
  @Test
  void shouldRemoveItem() {
    var instances = createInstances();
    var instanceToUse = instances.get(0);
    var itemToRemove = instanceToUse.getItems().get(1).getId();

    instanceToUse.getItems().forEach(item -> {
      if (item.getId().equals(itemToRemove)) {
        inventoryApi.deleteItem(TENANT_ID, item.getId());
        getByQuery("items.id=={value}", item.getId(), 0);
      } else {
        getByQuery("items.id=={value}", item.getId(), 1);
      }
    });
  }

  @Test
  void shouldRemoveHolding() {
    var instances = createInstances();
    var instanceToUse = instances.get(0);
    var hrToRemove = instanceToUse.getHoldings().get(0).getId();

    instanceToUse.getHoldings().forEach(hr -> {
      if (hr.getId().equals(hrToRemove)) {
        inventoryApi.deleteHolding(TENANT_ID, hr.getId());
        getByQuery("holdings.id=={value}", hr.getId(), 0);
      } else {
        getByQuery("holdings.id=={value}", hr.getId(), 1);
      }
    });
  }

  @Test
  void shouldRemoveInstance() {
    var instances = createInstances();
    var instanceToRemove = instances.get(0).getId();

    instances.forEach(instance -> {
      if (instance.getId().equals(instanceToRemove)) {
        inventoryApi.deleteInstance(TENANT_ID, instance.getId());
        getByQuery("id=={value}", instance.getId(), 0);
      } else {
        getByQuery("id=={value}", instance.getId(), 1);
      }
    });
  }

  private List<Instance> createInstances() {
    var instances = List.of(new Instance().id(randomId()),
      new Instance().id(randomId()),
      new Instance().id(randomId()));

    instances.get(0)
      .holdings(List.of(new Holding().id(randomId()), new Holding().id(randomId())))
      .items(List.of(new Item().id(randomId()), new Item().id(randomId())));

    instances.get(1)
      .holdings(List.of(new Holding().id(randomId()), new Holding().id(randomId())));

    instances.forEach(instance -> inventoryApi.createInstance(TENANT_ID, instance));
    getByQuery("id=={value}", instances.get(2).getId(), 1);

    return instances;
  }

  private void getByQuery(String query, String value, int expectedCount) {
    await().untilAsserted(() -> doGet(searchInstancesByQuery(query), value)
      .andExpect(jsonPath("totalRecords", is(expectedCount))));
  }
}
