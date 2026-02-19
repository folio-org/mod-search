package org.folio.indexing;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.service.reindex.ReindexConstants.CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.HOLDING_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_CALL_NUMBER_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.INSTANCE_TABLE;
import static org.folio.search.service.reindex.ReindexConstants.ITEM_TABLE;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;

import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.Holding;
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
@DatabaseCleanup(tenants = CENTRAL_TENANT_ID,
  tables = {CALL_NUMBER_TABLE, INSTANCE_CALL_NUMBER_TABLE, ITEM_TABLE, HOLDING_TABLE, INSTANCE_TABLE})
class IndexingInstanceCallNumberConsortiumIT extends BaseIntegrationTest {

  private static final String INSTANCE_ID = randomId();
  private static final String INSTANCE_TITLE = "title";
  private static final String CALL_NUMBER = "test";

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);

    enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_CALL_NUMBERS);

    setUpTestData();
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    deleteAllDocuments(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID);
  }

  @Test
  void shouldUpdateInstanceCallNumber_onInstanceSharing() {
    // given - fetch call number documents for the instance and check that tenant field contains member tenant id
    awaitAssertion(() -> assertInstanceCallNumberTenantId(MEMBER_TENANT_ID, false));

    // when - create instance in central tenant with the same instance id/title
    var centralInstance = new Instance().id(INSTANCE_ID).title(INSTANCE_TITLE).source("FOLIO");
    inventoryApi.createInstance(CENTRAL_TENANT_ID, centralInstance);

    // and - update member tenant instance to change source to have consortium prefix
    var memberInstance = new Instance().id(INSTANCE_ID).title(INSTANCE_TITLE).source("CONSORTIUM-FOLIO");
    inventoryApi.updateInstance(MEMBER_TENANT_ID, memberInstance);

    // then - fetch call number documents for the instance and check if tenant field changed to central
    awaitAssertion(() -> assertInstanceCallNumberTenantId(CENTRAL_TENANT_ID, true));
  }

  private void assertInstanceCallNumberTenantId(String expectedTenantId, boolean shared) {
    var hits = fetchAllDocuments(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID);
    assertThat(hits).hasSize(1);

    var sourceAsMap = hits[0].getSourceAsMap();
    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .hasSize(1)
      .allSatisfy(map -> assertThat(map)
        .containsEntry("tenantId", expectedTenantId)
        .containsEntry("shared", shared));
  }

  private static void setUpTestData() {
    var holdings = new Holding().id(randomId());
    var item = new Item().id(randomId()).holdingsRecordId(holdings.getId())
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(CALL_NUMBER));
    var instance = new Instance().id(INSTANCE_ID).title(INSTANCE_TITLE).source("FOLIO")
      .holdings(List.of(holdings))
      .items(List.of(item));
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), List.of(instance), 1, emptyList(),
      i -> inventoryApi.createInstance(MEMBER_TENANT_ID, i));
  }
}
