package org.folio.search.integration;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestConstants.inventoryHoldingTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestConstants.inventoryItemTopic;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.retry.support.RetryTemplate.defaultInstance;

import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.service.IndexService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaMessageListenerTest {

  @InjectMocks private KafkaMessageListener messageListener;
  @Mock private IndexService indexService;
  @Spy private final FolioMessageBatchProcessor batchProcessor =
    new FolioMessageBatchProcessor(emptyMap(), defaultInstance());

  @Test
  void handleEvents() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var instanceId3 = randomId();
    var instanceEvent1 = resourceEvent(null, mapOf("id", instanceId1, "title", "i1"));
    var instanceEvent2 = resourceEvent(null, mapOf("id", instanceId2, "title", "i2"));
    var itemEvent = resourceEvent(null, mapOf("id", randomId(), "instanceId", instanceId2));
    var holdingEvent1 = resourceEvent(null, mapOf("id", randomId(), "instanceId", instanceId3));
    var holdingEvent2 = resourceEvent(null, mapOf("id", randomId(), "instanceId", null));

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId1, instanceEvent1),
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId2, instanceEvent2),
      new ConsumerRecord<>(inventoryItemTopic(), 0, 0, instanceId2, itemEvent),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, instanceId3, holdingEvent1),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, null, holdingEvent2)));

    var expectedEvents = List.of(
      ResourceIdEvent.of(instanceId1, INSTANCE_RESOURCE, TENANT_ID, INDEX),
      ResourceIdEvent.of(instanceId2, INSTANCE_RESOURCE, TENANT_ID, INDEX),
      ResourceIdEvent.of(instanceId3, INSTANCE_RESOURCE, TENANT_ID, INDEX));

    verify(indexService).indexResourcesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "UPDATE", "REINDEX", "DELETE"})
  void handleEventsOnlyOfKnownTypes(String eventType) {
    var eventTypeEnumValue = ResourceEventType.fromValue(eventType);
    var instanceId = randomId();
    var resourceBody = resourceEvent(instanceId, null, mapOf("id", instanceId)).type(eventTypeEnumValue);
    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId, resourceBody)));

    var actionType = eventTypeEnumValue != ResourceEventType.DELETE ? INDEX : IndexActionType.DELETE;
    var expectedEvents = List.of(ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, TENANT_ID, actionType));

    verify(indexService).indexResourcesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleEvents_negative_shouldLogFailedEvent() {
    var instanceId = randomId();
    var instanceEvent = resourceEvent(INSTANCE_RESOURCE, mapOf("id", instanceId));
    var idEvent = ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, TENANT_ID, INDEX);
    when(indexService.indexResourcesById(List.of(idEvent))).thenThrow(new RuntimeException("failed to save"));

    messageListener.handleEvents(
      List.of(new ConsumerRecord<>(INVENTORY_INSTANCE_TOPIC, 0, 0, instanceId, instanceEvent)));
    verify(indexService, times(3)).indexResourcesById(List.of(idEvent));
  }

  @Test
  void handleEventsInstanceIdIsPartitionKeyForReindex() {
    var instanceId = UUID.randomUUID().toString();
    var instanceEvent = resourceEvent(INSTANCE_RESOURCE, null).type(REINDEX);

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId, instanceEvent)));

    var expectedEvents = List.of(ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, TENANT_ID, INDEX));
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
    verify(indexService).indexResourcesById(expectedEvents);
  }

  @Test
  void handleEvents_positive_itemDeleteEvent() {
    var itemEvent = new ResourceEvent().type(ResourceEventType.DELETE).tenant(TENANT_ID)
      .resourceName("item").old(mapOf("id", randomId(), "instanceId", RESOURCE_ID));
    var holdingEvent = new ResourceEvent().type(ResourceEventType.DELETE).tenant(TENANT_ID)
      .resourceName("holding").old(mapOf("id", randomId(), "instanceId", RESOURCE_ID));

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryItemTopic(), 0, 0, RESOURCE_ID, itemEvent),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, RESOURCE_ID, holdingEvent)));

    var expectedEvents = List.of(ResourceIdEvent.of(RESOURCE_ID, INSTANCE_RESOURCE, TENANT_ID, INDEX));
    verify(indexService).indexResourcesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_positive() {
    var authority = new Authority().id(RESOURCE_ID);
    var authorityEvent = new ResourceEvent().type(ResourceEventType.UPDATE).tenant(TENANT_ID)._new(toMap(authority));
    var expectedEvents = singletonList(authorityEvent);

    messageListener.handleAuthorityEvents(List.of(
      new ConsumerRecord<>(inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, authorityEvent)));

    verify(indexService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }
}
