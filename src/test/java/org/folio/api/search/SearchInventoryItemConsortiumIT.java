package org.folio.api.search;

import static org.apache.commons.collections4.MapUtils.getBooleanValue;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.domain.dto.RecordType.ITEMS;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER2_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.itemSearchPath;
import static org.folio.support.base.ApiEndpoints.recordFacetsPath;
import static org.folio.support.testdata.ConsortiumTestDataManager.LOCAL_INSTANCE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.SHARED_INSTANCE_ID;
import static org.folio.support.testdata.ConsortiumTestDataManager.items;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Consortium affiliation of {@code GET /search/inventory/items} over the item index populated from the shared ECS data
 * set: items of shared instances are returned to all tenants, items of local instances only to their owning tenant.
 * The assertions are scoped to the consortium search slice instances, so adding records does not change them.
 */
public abstract class SearchInventoryItemConsortiumIT extends BaseConsortiumIntegrationTest {

  private static final String DISCOVERY_SUPPRESS_FACET = "discoverySuppress";

  @Test
  void search_sharedInstance_returnsItemsOfAllTenants() throws Exception {
    var expected = fixtureItems(SHARED_INSTANCE_ID);
    var expectedIds = expected.stream().map(FixtureItem::id).toArray();
    var expectedTenants = expected.stream().map(FixtureItem::tenantId).toArray();

    for (var tenantId : List.of(CENTRAL_TENANT_ID, MEMBER_TENANT_ID, MEMBER2_TENANT_ID)) {
      doSearch(itemSearchPath(), tenantId, Map.of(QUERY_PARAM, "instanceId==" + SHARED_INSTANCE_ID,
        INCLUDE_PARAM, "tenantId,shared"))
        .andExpect(jsonPath("$.totalRecords", is(expected.size())))
        .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(expectedIds)))
        .andExpect(jsonPath("$.items[*].tenantId", containsInAnyOrder(expectedTenants)))
        .andExpect(jsonPath("$.items[0].shared").doesNotExist());
    }
  }

  @Test
  void search_localInstance_returnsItemsOnlyToOwningTenant() throws Exception {
    var expected = fixtureItems(LOCAL_INSTANCE_ID);
    var query = "instanceId==" + LOCAL_INSTANCE_ID;

    doSearch(itemSearchPath(), query, MEMBER_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(expected.size())))
      .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(expected.stream().map(FixtureItem::id).toArray())));
    doSearch(itemSearchPath(), query, MEMBER2_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(0)));
    doSearch(itemSearchPath(), query, CENTRAL_TENANT_ID)
      .andExpect(jsonPath("$.totalRecords", is(0)));
  }

  @Test
  void getFacets_localInstance_appliesAffiliation() throws Exception {
    var expected = fixtureItems(LOCAL_INSTANCE_ID);
    var notSuppressed = expected.stream().filter(item -> !item.discoverySuppress()).count();
    var facetsPath = recordFacetsPath(ITEMS, "instanceId==" + LOCAL_INSTANCE_ID, DISCOVERY_SUPPRESS_FACET);

    doGet(facetsPath, MEMBER_TENANT_ID)
      .andExpect(jsonPath("$.facets.discoverySuppress.values", hasSize(1)))
      .andExpect(jsonPath("$.facets.discoverySuppress.values[0].id", is("false")))
      .andExpect(jsonPath("$.facets.discoverySuppress.values[0].totalRecords", is((int) notSuppressed)));
    doGet(facetsPath, MEMBER2_TENANT_ID)
      .andExpect(jsonPath("$.facets.discoverySuppress.values", hasSize(0)));
  }

  /**
   * Item records of a consortium search slice instance in all tenants of the JSON data set.
   */
  private static List<FixtureItem> fixtureItems(String instanceId) {
    return Stream.of(CENTRAL_TENANT_ID, MEMBER_TENANT_ID, MEMBER2_TENANT_ID)
      .flatMap(tenantId -> items(tenantId).stream()
        .filter(item -> instanceId.equals(getString(item, INSTANCE_ID_FIELD)))
        .map(item -> new FixtureItem(getString(item, ID_FIELD), tenantId, getBooleanValue(item, "discoverySuppress"))))
      .toList();
  }

  private record FixtureItem(String id, String tenantId, boolean discoverySuppress) { }
}
