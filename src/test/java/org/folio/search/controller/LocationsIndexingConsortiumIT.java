package org.folio.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.ONE_MINUTE;
import static org.awaitility.Durations.ONE_SECOND;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.utils.SearchUtils.LOCATION_RESOURCE;
import static org.folio.search.utils.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.search.utils.TestConstants.MEMBER_TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryLocationTopic;
import static org.folio.search.utils.TestUtils.kafkaResourceEvent;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.toMap;

import java.io.IOException;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.dto.LocationDto;
import org.folio.search.support.base.BaseConsortiumIntegrationTest;
import org.folio.spring.testing.type.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@IntegrationTest
class LocationsIndexingConsortiumIT extends BaseConsortiumIntegrationTest {

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
    cleanUpIndex(LOCATION_RESOURCE, CENTRAL_TENANT_ID);
  }

  @Test
  void shouldIndexAndRemoveLocation() {
    var locationId = randomId();
    var location = LocationDto.builder().id(locationId)
      .name("location name")
      .code("CODE")
      .build();
    var createEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(location), null);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createEvent);
    awaitAssertLocationCount(1);

    var deleteEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, DELETE, null, toMap(location));
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), deleteEvent);
    awaitAssertLocationCount(0);
  }

  @Test
  void shouldIndexSameLocationFromDifferentTenantsAsSeparateDocs() {
    var locationId = randomId();
    var location = LocationDto.builder().id(locationId)
      .name("location name")
      .code("CODE")
      .build();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(location), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(location), null);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createMemberEvent);
    awaitAssertLocationCount(2);
  }

  @Test
  void shouldRemoveAllDocumentsByTenantIdOnDeleteAllEvent() {
    var locationId = randomId();
    var location = LocationDto.builder().id(locationId)
      .name("location name")
      .code("CODE")
      .build();
    var createCentralEvent = kafkaResourceEvent(CENTRAL_TENANT_ID, CREATE, toMap(location), null);
    var createMemberEvent = kafkaResourceEvent(MEMBER_TENANT_ID, CREATE, toMap(location), null);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createCentralEvent);
    kafkaTemplate.send(inventoryLocationTopic(CENTRAL_TENANT_ID), createMemberEvent);
    awaitAssertLocationCount(2);

    var deleteAllMemberEvent = new ResourceEvent().type(DELETE_ALL).tenant(MEMBER_TENANT_ID);
    kafkaTemplate.send(inventoryLocationTopic(MEMBER_TENANT_ID), deleteAllMemberEvent);
    awaitAssertLocationCount(1);
  }

  private void awaitAssertLocationCount(int expected) {
    await().atMost(ONE_MINUTE).pollInterval(ONE_SECOND).untilAsserted(() -> {
      var totalHits = countIndexDocument(LOCATION_RESOURCE, CENTRAL_TENANT_ID);
      assertThat(totalHits).isEqualTo(expected);
    });
  }

}
