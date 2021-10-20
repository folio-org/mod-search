package org.folio.search.controller;

import static java.util.stream.Collectors.toList;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS;
import static org.awaitility.Duration.ONE_MINUTE;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;
import static org.folio.search.sample.SampleInstances.getSemanticWebAsMap;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.stream.IntStream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.support.base.BaseIntegrationTest;
import org.folio.search.utils.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class IndexingIT extends BaseIntegrationTest {

  private static final List<String> INSTANCE_IDS = getRandomIds(3);
  private static final List<String> ITEM_IDS = getRandomIds(2);
  private static final List<String> HOLDING_IDS = getRandomIds(4);

  @BeforeAll
  static void prepare() {
    setUpTenant(Instance.class, getSemanticWebAsMap());
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
    assertCountByQuery("items.id=={value}", itemIdToDelete, 0);
    assertCountByQuery("items.id=={value}", ITEM_IDS.get(0), 1);
  }

  @Test
  void shouldRemoveHolding() {
    createInstances();
    inventoryApi.deleteHolding(TENANT_ID, HOLDING_IDS.get(0));
    assertCountByQuery("holdings.id=={value}", HOLDING_IDS.get(0), 0);
    HOLDING_IDS.subList(1, 4).forEach(id -> assertCountByQuery("holdings.id=={value}", id, 1));
  }

  @Test
  void shouldRemoveInstance() {
    createInstances();
    var instanceIdToDelete = INSTANCE_IDS.get(0);
    inventoryApi.deleteInstance(TENANT_ID, instanceIdToDelete);
    assertCountByQuery("id=={value}", instanceIdToDelete, 0);
    INSTANCE_IDS.subList(1, 3).forEach(id -> assertCountByQuery("id=={value}", id, 1));
  }

  private static void createInstances() {
    var instances = INSTANCE_IDS.stream()
      .map(id -> new Instance().id(id))
      .collect(toList());

    instances.get(0)
      .holdings(List.of(holding(0), holding(1)))
      .items(List.of(item(0), item(1)));

    instances.get(1).holdings(List.of(holding(2), holding(3)));

    instances.forEach(instance -> inventoryApi.createInstance(TENANT_ID, instance));
    assertCountByQuery("{value}", exactMatchAny("id", INSTANCE_IDS), 3);
  }

  private static Item item(int i) {
    return new Item().id(ITEM_IDS.get(i));
  }

  private static Holding holding(int i) {
    return new Holding().id(HOLDING_IDS.get(i));
  }

  private static void assertCountByQuery(String query, Object value, int expectedCount) {
    await().atMost(ONE_MINUTE).pollInterval(FIVE_HUNDRED_MILLISECONDS).untilAsserted(() ->
      doSearchByInstances(query.replace("{value}", String.valueOf(value)))
        .andExpect(jsonPath("$.totalRecords", is(expectedCount))));
  }

  private static List<String> getRandomIds(int count) {
    return IntStream.range(0, count).mapToObj(index -> randomId()).collect(toList());
  }
}
