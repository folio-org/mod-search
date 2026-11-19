package org.folio.indexing;

import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.utils.TestUtils.randomId;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Consortium item indexing tests that change data. Item search over the shared ECS data set (consortium affiliation of
 * shared and local instance items) is covered by {@code SearchInventoryItemConsortiumIT} in
 * {@code SearchBrowseConsortiumSuiteIT}.
 */
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

  private void createItem(String tenantId, String instanceId) {
    inventoryApi.createItem(tenantId, instanceId, new Item().id(randomId()).holdingsRecordId(randomId()));
  }

  private void assertItemsVisible(String tenantId, String instanceId, int expectedCount) {
    awaitAssertion(() -> doSearch(itemSearchPath(), "instanceId==" + instanceId, tenantId)
      .andExpect(jsonPath("$.totalRecords", is(expectedCount))));
  }
}
