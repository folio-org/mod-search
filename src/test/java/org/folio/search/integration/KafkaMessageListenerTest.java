package org.folio.search.integration;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestConstants.inventoryBoundWithTopic;
import static org.folio.search.utils.TestConstants.inventoryContributorTopic;
import static org.folio.search.utils.TestConstants.inventoryHoldingTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestConstants.inventoryItemTopic;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.retry.support.RetryTemplate.defaultInstance;

import java.util.List;
import java.util.function.BiConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.ContributorEvent;
import org.folio.search.service.ResourceService;
import org.folio.search.utils.JsonConverter;
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
  @Mock private ResourceService resourceService;
  @Spy private final FolioMessageBatchProcessor batchProcessor =
    new FolioMessageBatchProcessor(emptyMap(), defaultInstance());

  @Spy
  @SuppressWarnings("unused")
  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);

  @Test
  void handleEvents() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var instanceId3 = randomId();
    var instanceEvent1 = resourceEvent(null, null, mapOf("id", instanceId1, "title", "i1"));
    var instanceEvent2 = resourceEvent(null, null, mapOf("id", instanceId2, "title", "i2"));
    var itemEvent = resourceEvent(null, null, mapOf("id", randomId(), "instanceId", instanceId2));
    var holdingEvent1 = resourceEvent(null, null, mapOf("id", randomId(), "instanceId", instanceId3));
    var holdingEvent2 = resourceEvent(null, null, mapOf("id", randomId(), "instanceId", null));
    var boundWithEvent = resourceEvent(null, null, mapOf("id", randomId(), "instanceId", instanceId1));

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId1, instanceEvent1),
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId2, instanceEvent2),
      new ConsumerRecord<>(inventoryItemTopic(), 0, 0, instanceId2, itemEvent),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, instanceId3, holdingEvent1),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, null, holdingEvent2),
      new ConsumerRecord<>(inventoryBoundWithTopic(), 0, 0, instanceId1, boundWithEvent)));

    var expectedEvents = List.of(
      resourceEvent(instanceId1, INSTANCE_RESOURCE, CREATE, instanceEvent1.getNew(), null),
      resourceEvent(instanceId2, INSTANCE_RESOURCE, CREATE, instanceEvent2.getNew(), null),
      resourceEvent(instanceId2, INSTANCE_RESOURCE, CREATE, itemEvent.getNew(), null),
      resourceEvent(instanceId3, INSTANCE_RESOURCE, CREATE, holdingEvent1.getNew(), null),
      resourceEvent(instanceId1, INSTANCE_RESOURCE, CREATE, boundWithEvent.getNew(), null)
    );

    verify(resourceService).indexResourcesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "UPDATE", "REINDEX", "DELETE"})
  void handleEventsOnlyOfKnownTypes(String eventType) {
    var eventTypeEnumValue = ResourceEventType.fromValue(eventType);
    var resourceBody = resourceEvent(null, null, mapOf("id", RESOURCE_ID)).type(eventTypeEnumValue);

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, RESOURCE_ID, resourceBody)));

    var expectedEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, eventTypeEnumValue, resourceBody.getNew(), null);
    var expectedEvents = List.of(expectedEvent);

    verify(resourceService).indexResourcesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleEvents_negative_shouldLogFailedEvent() {
    var expectedEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID));
    when(resourceService.indexResourcesById(List.of(expectedEvent))).thenThrow(new RuntimeException("failed to save"));

    var instanceEvent = resourceEvent(null, null, mapOf("id", RESOURCE_ID));
    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(INVENTORY_INSTANCE_TOPIC, 0, 0, RESOURCE_ID, instanceEvent)));
    verify(resourceService, times(3)).indexResourcesById(List.of(expectedEvent));
  }

  @Test
  void handleEvents_positive_reindexEventWithoutBody() {

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, null, REINDEX))));

    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, REINDEX));
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
    verify(resourceService).indexResourcesById(expectedEvents);
  }

  @Test
  void handleEvents_positive_itemAndHoldingDeleteEvents() {
    var itemPayload = mapOf("id", randomId(), "instanceId", RESOURCE_ID);
    var holdingPayload = mapOf("id", randomId(), "instanceId", RESOURCE_ID);
    var itemEvent = resourceEvent(null, null, DELETE, null, itemPayload);
    var holdingEvent = resourceEvent(null, null, DELETE, null, holdingPayload);

    messageListener.handleEvents(List.of(
      new ConsumerRecord<>(inventoryItemTopic(), 0, 0, RESOURCE_ID, itemEvent),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, RESOURCE_ID, holdingEvent)));

    var expectedEvents = List.of(
      resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, CREATE, null, itemPayload),
      resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, CREATE, null, holdingPayload));
    verify(resourceService).indexResourcesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_positive() {
    var payload = toMap(new Authority().id(RESOURCE_ID));

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, null, CREATE, payload, null))));

    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, AUTHORITY_RESOURCE, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_negative_logFailedEvent() {
    var payload = toMap(new Authority().id(RESOURCE_ID).personalName("test"));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, AUTHORITY_RESOURCE, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3).accept(expectedEvents.get(0), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, null, UPDATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleContributorEvent_positive() {
    var contributorEventBuilder = ContributorEvent.builder()
      .id(RESOURCE_ID).name(randomId()).nameTypeId(randomId()).instanceId(randomId()).build();
    var payload = toMap(contributorEventBuilder);
    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, CONTRIBUTOR_RESOURCE, CREATE, payload, null));

    messageListener.handleContributorEvents(List.of(new ConsumerRecord<>(
      inventoryContributorTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, null, CREATE, payload, null))));

    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleContributorEvent_negative_logFailedEvent() {
    var contributorEventBuilder = ContributorEvent.builder()
      .id(RESOURCE_ID).name(randomId()).nameTypeId(randomId()).instanceId(randomId()).build();
    var payload = toMap(contributorEventBuilder);
    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, CONTRIBUTOR_RESOURCE, CREATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3).accept(expectedEvents.get(0), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    messageListener.handleContributorEvents(List.of(new ConsumerRecord<>(
      inventoryContributorTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, null, CREATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }
}
