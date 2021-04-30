package org.folio.search.integration;

import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.eventBody;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.domain.dto.ResourceEventBody.TypeEnum;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  @InjectMocks private KafkaMessageListener messageListener;
  @Mock private IndexService indexService;

  @Test
  void handleEvents() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var instanceId3 = randomId();
    var instanceBody1 = eventBody(null, mapOf("id", instanceId1, "title", "i1"));
    var instanceBody2 = eventBody(null, mapOf("id", instanceId2, "title", "i2"));
    var itemBody = eventBody(null, mapOf("id", randomId(), "instanceId", instanceId2));
    var holdingBody1 = eventBody(null, mapOf("id", randomId(), "instanceId", instanceId3));
    var holdingBody2 = eventBody(null, mapOf("id", randomId(), "instanceId", null));

    var resourceIdEvents = List.of(
      ResourceIdEvent.of(instanceId1, INSTANCE_RESOURCE, TENANT_ID, INDEX),
      ResourceIdEvent.of(instanceId2, INSTANCE_RESOURCE, TENANT_ID, INDEX),
      ResourceIdEvent.of(instanceId3, INSTANCE_RESOURCE, TENANT_ID, INDEX));

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>("inventory.instance", 0, 0, instanceId1, instanceBody1),
      new ConsumerRecord<>("inventory.instance", 0, 0, instanceId2, instanceBody2),
      new ConsumerRecord<>("inventory.item", 0, 0, instanceId2, itemBody),
      new ConsumerRecord<>("inventory.holding-record", 0, 0, instanceId3, holdingBody1),
      new ConsumerRecord<>("inventory.holding-record", 0, 0, null, holdingBody2)
    ));

    verify(indexService).indexResourcesById(resourceIdEvents);
  }

  @ValueSource(strings = {"CREATE", "UPDATE", "REINDEX"})
  @ParameterizedTest
  void handleEventsOnlyOfKnownTypes(String eventType) {
    var resourceBody = new ResourceEventBody()
      .type(ResourceEventBody.TypeEnum.fromValue(eventType))
      .tenant(TENANT_ID)
      ._new(null);

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(INVENTORY_INSTANCE_TOPIC, 0, 0,
        UUID.randomUUID().toString(), resourceBody)));

    verify(indexService).indexResourcesById(any());
  }

  @Test
  void handleEventsInstanceIdIsPartitionKeyForReindex() {
    var instanceId = UUID.randomUUID().toString();
    var resourceBody = new ResourceEventBody()
      .type(ResourceEventBody.TypeEnum.REINDEX)
      .tenant(TENANT_ID)
      .resourceName(INSTANCE_RESOURCE)
      ._new(null);

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>("inventory.instance", 0, 0,
        instanceId, resourceBody)));

    var expectedEvent = ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, TENANT_ID, INDEX);
    verify(indexService).indexResourcesById(List.of(expectedEvent));
  }

  @Test
  void handleEvents_positive_itemDeleteEvent() {
    var itemBody = new ResourceEventBody().type(TypeEnum.DELETE).tenant(TENANT_ID)
      .resourceName("item").old(mapOf("id", randomId(), "instanceId", RESOURCE_ID));
    var holdingBody = new ResourceEventBody().type(TypeEnum.DELETE).tenant(TENANT_ID)
      .resourceName("holding").old(mapOf("id", randomId(), "instanceId", RESOURCE_ID));

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>("inventory.item", 0, 0, RESOURCE_ID, itemBody),
      new ConsumerRecord<>("inventory.holding-record", 0, 0, RESOURCE_ID, holdingBody)));

    var expectedEvent = ResourceIdEvent.of(RESOURCE_ID, INSTANCE_RESOURCE, TENANT_ID, INDEX);
    verify(indexService).indexResourcesById(List.of(expectedEvent));
  }
}
