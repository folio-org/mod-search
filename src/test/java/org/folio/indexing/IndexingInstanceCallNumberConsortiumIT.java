package org.folio.indexing;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.folio.search.model.types.ResourceType.INSTANCE;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.event.InstanceSharingCompleteEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.extension.DatabaseCleanup;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.search-config.indexing.instance-children-index-enabled=true")
@DatabaseCleanup(tenants = CENTRAL_TENANT_ID,
  tables = {CALL_NUMBER_TABLE, INSTANCE_CALL_NUMBER_TABLE, ITEM_TABLE, HOLDING_TABLE, INSTANCE_TABLE})
class IndexingInstanceCallNumberConsortiumIT extends BaseIntegrationTest {

  private static final String INSTANCE_ID = randomId();
  private static final String LOCATION_ID = randomId();
  private static final String INSTANCE_TITLE = "title";
  private static final String CALL_NUMBER = "test";

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);

    enableFeature(CENTRAL_TENANT_ID, TenantConfiguredFeature.BROWSE_CALL_NUMBERS);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(MEMBER_TENANT_ID);
    removeTenant(CENTRAL_TENANT_ID);
  }

  @AfterEach
  void tearDown() {
    deleteAllDocuments(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID);
    awaitAssertion(() -> assertDocumentsEmpty(INSTANCE_CALL_NUMBER));
    deleteAllDocuments(INSTANCE, CENTRAL_TENANT_ID);
    awaitAssertion(() -> assertDocumentsEmpty(INSTANCE));
  }

  @Test
  void shouldUpdateInstanceCallNumber_onInstanceSharing() {
    // given
    createInstanceInMemberTenant(INSTANCE_ID, INSTANCE_TITLE, LOCATION_ID, CALL_NUMBER);
    awaitAssertion(() -> assertInstanceCallNumberSharedState(false));
    // when - create instance in central tenant with the same instance id/title
    var centralInstance = new Instance().id(INSTANCE_ID).title(INSTANCE_TITLE).source("FOLIO");
    inventoryApi.createInstance(CENTRAL_TENANT_ID, centralInstance);

    // and - update member tenant instance to change source to have consortium prefix
    var memberInstance = new Instance().id(INSTANCE_ID).title(INSTANCE_TITLE).source("CONSORTIUM-FOLIO");
    inventoryApi.updateInstance(MEMBER_TENANT_ID, memberInstance);

    // then - fetch call number documents for the instance and check if tenant field changed to member tenant id
    awaitAssertion(() -> assertInstanceCallNumberSharedState(false));

    inventoryApi.shareInstance(CENTRAL_TENANT_ID, INSTANCE_ID, InstanceSharingCompleteEvent.Status.COMPLETE, "",
      MEMBER_TENANT_ID, CENTRAL_TENANT_ID);

    // then - check that shared field is set to true
    awaitAssertion(() -> assertInstanceCallNumberSharedState(true));
  }

  @ParameterizedTest(name = "{index} => status={0}, errorMessage={1}, targetTenant={2}")
  @MethodSource("negativeSharingScenarios")
  void shouldNotUpdateInstanceCallNumber_onInvalidSharingEvent(
    InstanceSharingCompleteEvent.Status status,
    String errorMessage,
    String targetTenantId,
    String instanceId, String title, String locationId, String callNumber
  ) {
    // given
    createInstanceInMemberTenant(instanceId, title, locationId, callNumber);

    awaitAssertion(() -> assertInstanceCallNumberSharedState(false));

    var centralInstance = new Instance().id(instanceId).title(title).source("FOLIO");
    inventoryApi.createInstance(CENTRAL_TENANT_ID, centralInstance);

    var memberInstance = new Instance().id(instanceId).title(title).source("CONSORTIUM-FOLIO");
    inventoryApi.updateInstance(MEMBER_TENANT_ID, memberInstance);

    awaitAssertion(() -> assertInstanceCallNumberSharedState(false));

    // when
    inventoryApi.shareInstance(MEMBER_TENANT_ID, instanceId, status, errorMessage, MEMBER_TENANT_ID, targetTenantId);

    // then check that the shared field is not updated and remains false
    await()
      .pollDelay(Duration.ofSeconds(30))
      .atMost(ONE_MINUTE)
      .untilAsserted(() ->
        assertInstanceCallNumberSharedState(false)
      );
  }

  private static Stream<Arguments> negativeSharingScenarios() {
    return Stream.of(
      // ERROR status
      Arguments.of(InstanceSharingCompleteEvent.Status.ERROR, "", CENTRAL_TENANT_ID,
        UUID.randomUUID().toString(), "title1", UUID.randomUUID().toString(), "call number1"),
      // error message is present
      Arguments.of(InstanceSharingCompleteEvent.Status.COMPLETE, "error message", CENTRAL_TENANT_ID,
        UUID.randomUUID().toString(), "title2", UUID.randomUUID().toString(), "call number2"),
      // target tenant is not central tenant
      Arguments.of(InstanceSharingCompleteEvent.Status.COMPLETE, "", MEMBER_TENANT_ID,
        UUID.randomUUID().toString(), "title3", UUID.randomUUID().toString(), "call number3"));
  }

  private static void assertInstanceCallNumberSharedState(boolean shared) {
    var hits = fetchAllDocuments(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID);
    assertThat(hits).hasSize(1);

    var sourceAsMap = hits[0].getSourceAsMap();
    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .hasSize(1)
      .allSatisfy(map -> assertThat(map)
        .containsEntry("tenantId", MEMBER_TENANT_ID)
        .containsEntry("shared", shared));
  }

  private static void assertDocumentsEmpty(ResourceType resourceType) {
    var hits = fetchAllDocuments(resourceType, CENTRAL_TENANT_ID);
    assertThat(hits).isEmpty();
  }

  private static void createInstanceInMemberTenant(String instanceId, String instanceTitle,
                                                   String locationId, String callNumber) {
    var holdings = new Holding().id(randomId());
    var item = new Item().id(randomId()).holdingsRecordId(holdings.getId())
      .effectiveLocationId(locationId)
      .effectiveCallNumberComponents(new ItemEffectiveCallNumberComponents().callNumber(callNumber));
    var instance = new Instance().id(instanceId).title(instanceTitle).source("FOLIO")
      .holdings(List.of(holdings))
      .items(List.of(item));
    saveRecords(MEMBER_TENANT_ID, instanceSearchPath(), List.of(instance), 1, emptyList(),
      i -> inventoryApi.createInstance(MEMBER_TENANT_ID, i));
  }
}
