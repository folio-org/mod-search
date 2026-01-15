package org.folio.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.model.types.ResourceType.LOCATION;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.MEMBER_TENANT_ID;
import static org.folio.support.TestConstants.inventoryLibraryTopic;
import static org.folio.support.TestConstants.inventoryLocationTopic;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.kafkaResourceEvent;
import static org.folio.support.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.folio.search.domain.dto.Metadata;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.message.KafkaMessageListener;
import org.folio.search.model.dto.LocationDto;
import org.folio.spring.testing.type.IntegrationTest;
import org.folio.support.base.BaseConsortiumIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@IntegrationTest
class IndexingLocationsConsortiumIT extends BaseConsortiumIntegrationTest {

  @MockitoSpyBean private KafkaMessageListener kafkaMessageListener;

  @BeforeAll
  static void prepare() {
    setUpTenant(CENTRAL_TENANT_ID);
    setUpTenant(MEMBER_TENANT_ID);
  }

  @AfterAll
  static void cleanUp() {
    removeTenant(CENTRAL_TENANT_ID);
    removeTenant(MEMBER_TENANT_ID);
  }

  @AfterEach
  void tearDown() throws IOException {
    cleanUpIndex(LOCATION, CENTRAL_TENANT_ID);
    reset(kafkaMessageListener);
  }

  @Test
  void shouldIndexAndRemoveLocation() {
    var location = location();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(location), null);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createEvent);
    awaitAssertLocationCount(1);

    var deleteEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, DELETE, null, toMap(location));
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), deleteEvent);
    awaitAssertLocationCount(0);
  }

  @Test
  void shouldIndexSameLocationFromDifferentTenantsAsSeparateDocs() {
    var location = location();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(location), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(location), null);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createMemberEvent);
    awaitAssertLocationCount(2);
  }

  @Test
  void shouldRemoveAllDocumentsByTenantIdOnDeleteAllEvent() {
    var location = location();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(location), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(location), null);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createMemberEvent);
    awaitAssertLocationCount(2);

    var deleteAllMemberEvent = new ResourceEvent().type(DELETE_ALL).tenant(MEMBER_TENANT_ID);
    kafkaTemplate.send(inventoryLocationTopic(MEMBER_TENANT_ID), deleteAllMemberEvent);
    awaitAssertLocationCount(1);
  }

  @Test
  void shouldIgnoreShadowInstitution() {
    var libraryMap = toMap(location());
    libraryMap.put("isShadow", true);
    var visited = new AtomicBoolean(false);

    doAnswer(inv -> {
      visited.set(true);
      return inv.callRealMethod();
    }).when(kafkaMessageListener).handleLocationEvents(anyList());

    var libraryEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, libraryMap, null);
    kafkaTemplate.send(inventoryLibraryTopic(MEMBER_TENANT_ID), libraryEvent);

    await().atMost(ONE_MINUTE)
      .pollInterval(ONE_HUNDRED_MILLISECONDS)
      .untilAsserted(() -> assertThat(visited.get()).isTrue());

    awaitAssertLocationCount(0);
  }

  public static void awaitAssertLocationCount(int expected) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(LOCATION, CENTRAL_TENANT_ID);
      assertThat(totalHits).isEqualTo(expected);
    });
  }

  private LocationDto location() {
    var id = randomId();
    return LocationDto.builder().id(id)
      .name("location name")
      .code("CODE")
      .campusId(id)
      .institutionId(id)
      .description("desc")
      .discoveryDisplayName("display name")
      .primaryServicePoint(UUID.fromString(id))
      .isActive(true)
      .servicePointIds(List.of(UUID.fromString(id)))
      .metadata(new Metadata()
        .createdDate("2021-03-01T00:00:00.000+00:00")
        .updatedDate("2021-03-01T00:00:00.000+00:00"))
      .build();
  }
}
