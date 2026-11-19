package org.folio.indexing;

import static org.folio.search.domain.dto.RecordType.ITEMS;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.LinkedHashMap;
import java.util.Map;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = {
  "folio.search-config.indexing.item-index-enabled=true"
})
class IndexingItemConsortiumIT extends BaseIntegrationTest {

  @BeforeAll
  static void prepare() {
    enableTenant(CENTRAL_TENANT_ID);
    enableTenant(MEMBER_TENANT_ID);
    enableTenant(MEMBER2_TENANT_ID);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER2_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @Test
  void shouldReturnItemsOfSharedInstanceFromAllTenants() {
    var instanceId = createInstance(CENTRAL_TENANT_ID);
    createItem(CENTRAL_TENANT_ID, instanceId);
    createSharedInstanceItem(MEMBER_TENANT_ID, instanceId);
    createSharedInstanceItem(MEMBER2_TENANT_ID, instanceId);

    assertItemsVisible(CENTRAL_TENANT_ID, instanceId, 3);
    assertItemsVisible(MEMBER_TENANT_ID, instanceId, 3);
    assertItemsVisible(MEMBER2_TENANT_ID, instanceId, 3);

    awaitAssertion(() -> doSearch(itemSearchPath(), MEMBER2_TENANT_ID,
      Map.of("query", "instanceId==" + instanceId, "include", "tenantId,shared"))
      .andExpect(jsonPath("$.items[0].shared").doesNotExist())
      .andExpect(jsonPath("$.items[*].tenantId",
        containsInAnyOrder(CENTRAL_TENANT_ID, MEMBER_TENANT_ID, MEMBER2_TENANT_ID))));
  }

  @Test
  void shouldReturnItemsOfLocalInstanceOnlyToOwningTenant() {
    var instanceId = createInstance(MEMBER_TENANT_ID);
    createItem(MEMBER_TENANT_ID, instanceId);

    assertItemsVisible(MEMBER_TENANT_ID, instanceId, 1);
    assertItemsVisible(MEMBER2_TENANT_ID, instanceId, 0);
    assertItemsVisible(CENTRAL_TENANT_ID, instanceId, 0);
  }

  @Test
  void shouldApplyAffiliationToFacets() throws Exception {
    var instanceId = createInstance(MEMBER_TENANT_ID);
    createItem(MEMBER_TENANT_ID, instanceId);
    assertItemsVisible(MEMBER_TENANT_ID, instanceId, 1);

    var facetsPath = recordFacetsPath(ITEMS, "instanceId==" + instanceId, "discoverySuppress");
    awaitAssertion(() -> doGet(facetsPath, MEMBER_TENANT_ID)
      .andExpect(jsonPath("$.facets.discoverySuppress.values[0].id", is("false")))
      .andExpect(jsonPath("$.facets.discoverySuppress.values[0].totalRecords", is(1))));
    doGet(facetsPath, MEMBER2_TENANT_ID)
      .andExpect(jsonPath("$.facets.discoverySuppress.values", hasSize(0)));
  }

  @Test
  void shouldReturnExistingItemsToAllTenantsWhenLocalInstanceBecomesShared() {
    var instanceId = createInstance(MEMBER_TENANT_ID);
    createItem(MEMBER_TENANT_ID, instanceId);
    assertItemsVisible(MEMBER_TENANT_ID, instanceId, 1);
    assertItemsVisible(MEMBER2_TENANT_ID, instanceId, 0);

    inventoryApi.createInstance(CENTRAL_TENANT_ID, new Instance().id(instanceId).title("shared-" + instanceId));

    assertItemsVisible(MEMBER2_TENANT_ID, instanceId, 1);
    assertItemsVisible(CENTRAL_TENANT_ID, instanceId, 1);
    assertItemsVisible(MEMBER_TENANT_ID, instanceId, 1);
  }

  private String createInstance(String tenantId) {
    var instanceId = randomId();
    inventoryApi.createInstance(tenantId, new Instance().id(instanceId).title("title-" + instanceId));
    awaitAssertion(() -> doSearchInstances("id==" + instanceId, tenantId)
      .andExpect(jsonPath("$.totalRecords", is(1))));
    return instanceId;
  }

  private void createSharedInstanceItem(String tenantId, String instanceId) {
    var item = new LinkedHashMap<String, Object>();
    item.put("id", randomId());
    item.put("holdingsRecordId", randomId());
    item.put("instanceSource", "CONSORTIUM-MARC");
    inventoryApi.createItem(tenantId, instanceId, item);
  }

  private void createItem(String tenantId, String instanceId) {
    inventoryApi.createItem(tenantId, instanceId, new Item().id(randomId()).holdingsRecordId(randomId()));
  }

  private void assertItemsVisible(String tenantId, String instanceId, int expectedCount) {
    awaitAssertion(() -> doSearch(itemSearchPath(), "instanceId==" + instanceId, tenantId)
      .andExpect(jsonPath("$.totalRecords", is(expectedCount))));
  }
}
