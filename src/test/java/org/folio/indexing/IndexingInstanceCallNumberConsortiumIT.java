package org.folio.indexing;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.folio.search.model.types.ResourceType.INSTANCE_CALL_NUMBER;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.base.ApiEndpoints.instanceSearchPath;
import static org.folio.support.utils.TestUtils.randomId;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.search.domain.dto.Holding;
import org.folio.search.domain.dto.Instance;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ItemEffectiveCallNumberComponents;
import org.folio.search.model.event.InstanceSharingCompleteEvent;
import org.folio.support.base.BaseSharedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.search.SearchHit;

/**
 * Runs in a suite together with other indexing tests, so every test uses its own instance and call number and the
 * assertions are scoped to that call number.
 */
public abstract class IndexingInstanceCallNumberConsortiumIT extends BaseSharedTest {

  private static final String INSTANCE_ID = randomId();
  private static final String LOCATION_ID = randomId();
  private static final String INSTANCE_TITLE = "title";
  private static final String CALL_NUMBER = "test";
  private static final String FULL_CALL_NUMBER_FIELD = "fullCallNumber";

  @Test
  void shouldUpdateInstanceCallNumber_onInstanceSharing() {
    // given - member instance with a call number, the central instance with the same id/title and the member instance
    // updated to a shadow copy
    prepareInstanceSharing(INSTANCE_ID, INSTANCE_TITLE, LOCATION_ID, CALL_NUMBER);

    // when
    inventoryApi.shareInstance(CENTRAL_TENANT_ID, INSTANCE_ID, InstanceSharingCompleteEvent.Status.COMPLETE, "",
      MEMBER_TENANT_ID, CENTRAL_TENANT_ID);

    // then - check that shared field is set to true
    awaitAssertion(() -> assertInstanceCallNumberSharedState(CALL_NUMBER, true));
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
    prepareInstanceSharing(instanceId, title, locationId, callNumber);
    var sentinelInstanceId = randomId();
    var sentinelCallNumber = callNumber + " sentinel";
    prepareInstanceSharing(sentinelInstanceId, title + " sentinel", randomId(), sentinelCallNumber);

    // when
    inventoryApi.shareInstance(MEMBER_TENANT_ID, instanceId, status, errorMessage, MEMBER_TENANT_ID, targetTenantId);
    // a valid sharing event of the sentinel instance is sent after the tested event to the same single-partition
    // topic, so it is consumed after it: once the sentinel call number is shared, the tested event is processed too
    inventoryApi.shareInstance(MEMBER_TENANT_ID, sentinelInstanceId, InstanceSharingCompleteEvent.Status.COMPLETE, "",
      MEMBER_TENANT_ID, CENTRAL_TENANT_ID);

    // then check that the shared field is not updated and remains false
    awaitAssertion(() -> assertInstanceCallNumberSharedState(sentinelCallNumber, true));
    assertInstanceCallNumberSharedState(callNumber, false);
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

  /**
   * Creates a member instance with a call number, then the central instance with the same id/title, and updates the
   * member instance to a shadow copy (consortium source), checking that the call number stays not shared until the
   * sharing complete event.
   */
  private static void prepareInstanceSharing(String instanceId, String title, String locationId, String callNumber) {
    createInstanceInMemberTenant(instanceId, title, locationId, callNumber);
    awaitAssertion(() -> assertInstanceCallNumberSharedState(callNumber, false));

    var centralInstance = new Instance().id(instanceId).title(title).source("FOLIO");
    inventoryApi.createInstance(CENTRAL_TENANT_ID, centralInstance);

    var memberInstance = new Instance().id(instanceId).title(title).source("CONSORTIUM-FOLIO");
    inventoryApi.updateInstance(MEMBER_TENANT_ID, memberInstance);

    awaitAssertion(() -> assertInstanceCallNumberSharedState(callNumber, false));
  }

  private static void assertInstanceCallNumberSharedState(String callNumber, boolean shared) {
    var hits = fetchCallNumberDocuments(callNumber);
    assertThat(hits).hasSize(1);

    var sourceAsMap = hits.getFirst().getSourceAsMap();
    @SuppressWarnings("unchecked")
    var instances = (List<Map<String, Object>>) sourceAsMap.get("instances");
    assertThat(instances)
      .hasSize(1)
      .allSatisfy(map -> assertThat(map)
        .containsEntry("tenantId", MEMBER_TENANT_ID)
        .containsEntry("shared", shared));
  }

  /**
   * Returns the call number documents of the central tenant index with the given full call number.
   */
  @SneakyThrows
  private static List<SearchHit> fetchCallNumberDocuments(String callNumber) {
    var searchRequest = new SearchRequest()
      .source(searchSource().query(termQuery(FULL_CALL_NUMBER_FIELD, callNumber)))
      .indices(getIndexName(INSTANCE_CALL_NUMBER, CENTRAL_TENANT_ID));
    return Arrays.stream(elasticClient.search(searchRequest, RequestOptions.DEFAULT).getHits().getHits())
      .filter(hit -> callNumber.equals(hit.getSourceAsMap().get(FULL_CALL_NUMBER_FIELD)))
      .toList();
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
    inventoryApi.createInstance(MEMBER_TENANT_ID, instance);
    assertSearchByIdsCount(instanceSearchPath(), List.of(instanceId), 1, MEMBER_TENANT_ID);
  }
}
