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
import static org.folio.search.utils.SearchUtils.LINKED_DATA_AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.LINKED_DATA_WORK_RESOURCE;
import static org.folio.search.utils.TestConstants.INVENTORY_INSTANCE_TOPIC;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestConstants.consortiumInstanceTopic;
import static org.folio.search.utils.TestConstants.inventoryAuthorityTopic;
import static org.folio.search.utils.TestConstants.inventoryBoundWithTopic;
import static org.folio.search.utils.TestConstants.inventoryClassificationTopic;
import static org.folio.search.utils.TestConstants.inventoryContributorTopic;
import static org.folio.search.utils.TestConstants.inventoryHoldingTopic;
import static org.folio.search.utils.TestConstants.inventoryInstanceTopic;
import static org.folio.search.utils.TestConstants.inventoryItemTopic;
import static org.folio.search.utils.TestConstants.linkedDataAuthorityTopic;
import static org.folio.search.utils.TestConstants.linkedDataWorkTopic;
import static org.folio.search.utils.TestUtils.OBJECT_MAPPER;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.toMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.retry.support.RetryTemplate.defaultInstance;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.LinkedDataAuthority;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.ConsortiumInstanceEvent;
import org.folio.search.model.event.ContributorResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
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

  @Spy
  private final FolioMessageBatchProcessor batchProcessor =
    new FolioMessageBatchProcessor(emptyMap(), defaultInstance());
  @Spy
  @SuppressWarnings("unused")
  private final JsonConverter jsonConverter = new JsonConverter(OBJECT_MAPPER);
  @InjectMocks
  private KafkaMessageListener messageListener;
  @Mock
  private ResourceService resourceService;
  @Mock
  private ConfigSynchronizationService configSynchronizationService;
  @Mock
  private SystemUserScopedExecutionService executionService;

  @BeforeEach
  void setUp() {
    lenient().doAnswer(invocation -> ((Callable<?>) invocation.getArgument(1)).call())
      .when(executionService).executeSystemUserScoped(any(), any());
  }

  @Test
  void handleEvents() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var instanceId3 = randomId();
    var instanceEvent1 = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", instanceId1, "title", "i1"));
    var instanceEvent2 = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", instanceId2, "title", "i2"));
    var itemEvent = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", randomId(), "instanceId", instanceId2));
    var holdingEvent1 = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", randomId(), "instanceId", instanceId3));
    var holdingEvent2 = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", randomId(), "instanceId", null));
    var boundWithEvent = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", randomId(), "instanceId", instanceId1));

    messageListener.handleInstanceEvents(List.of(
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

    verify(resourceService).indexInstancesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "UPDATE", "REINDEX", "DELETE"})
  void handleEventsOnlyOfKnownTypes(String eventType) {
    var eventTypeEnumValue = ResourceEventType.fromValue(eventType);
    var resourceBody = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID)).type(eventTypeEnumValue);

    messageListener.handleInstanceEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, RESOURCE_ID, resourceBody)));

    var expectedEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, eventTypeEnumValue, resourceBody.getNew(), null);
    var expectedEvents = List.of(expectedEvent);

    verify(resourceService).indexInstancesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleEvents_negative_shouldLogFailedEvent() {
    var expectedEvent = resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID));
    when(resourceService.indexInstancesById(List.of(expectedEvent))).thenThrow(new RuntimeException("failed to save"));

    var instanceEvent = resourceEvent(null, INSTANCE_RESOURCE, mapOf("id", RESOURCE_ID));
    messageListener.handleInstanceEvents(List.of(
      new ConsumerRecord<>(INVENTORY_INSTANCE_TOPIC, 0, 0, RESOURCE_ID, instanceEvent)));
    verify(resourceService, times(3)).indexInstancesById(List.of(expectedEvent));
  }

  @Test
  void handleEvents_positive_reindexEventWithoutBody() {

    messageListener.handleInstanceEvents(List.of(
      new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, RESOURCE_ID,
        resourceEvent(null, INSTANCE_RESOURCE, REINDEX))));

    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, REINDEX));
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
    verify(resourceService).indexInstancesById(expectedEvents);
  }

  @Test
  void handleEvents_positive_itemAndHoldingDeleteEvents() {
    var itemPayload = mapOf("id", randomId(), "instanceId", RESOURCE_ID);
    var holdingPayload = mapOf("id", randomId(), "instanceId", RESOURCE_ID);
    var itemEvent = resourceEvent(null, INSTANCE_RESOURCE, DELETE, null, itemPayload);
    var holdingEvent = resourceEvent(null, INSTANCE_RESOURCE, DELETE, null, holdingPayload);

    messageListener.handleInstanceEvents(List.of(
      new ConsumerRecord<>(inventoryItemTopic(), 0, 0, RESOURCE_ID, itemEvent),
      new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, RESOURCE_ID, holdingEvent)));

    var expectedEvents = List.of(
      resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, CREATE, null, itemPayload),
      resourceEvent(RESOURCE_ID, INSTANCE_RESOURCE, CREATE, null, holdingPayload));
    verify(resourceService).indexInstancesById(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_positive() {
    var payload = toMap(new Authority().id(RESOURCE_ID));

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, AUTHORITY_RESOURCE, CREATE, payload, null))));

    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, AUTHORITY_RESOURCE, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_positive_shouldSkipAuthorityShadowCopies() {
    var payload = toMap(new Authority().id(RESOURCE_ID).source("CONSORTIUM-MARC"));

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, null, REINDEX, payload, null))));

    verify(resourceService, never()).indexResources(anyList());
    verify(batchProcessor, never()).consumeBatchWithFallback(any(), any(), any(), any());
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
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, AUTHORITY_RESOURCE, UPDATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleContributorEvent_positive() {
    var contributorEventBuilder = ContributorResourceEvent.builder()
      .id(RESOURCE_ID).name(randomId()).nameTypeId(randomId()).instanceId(randomId()).build();
    var payload = toMap(contributorEventBuilder);
    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, CONTRIBUTOR_RESOURCE, CREATE, payload, null));

    messageListener.handleContributorEvents(List.of(new ConsumerRecord<>(
      inventoryContributorTopic(), 0, 0, RESOURCE_ID,
      resourceEvent(null, CONTRIBUTOR_RESOURCE, CREATE, payload, null))));

    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleContributorEvent_negative_logFailedEvent() {
    var contributorEventBuilder = ContributorResourceEvent.builder()
      .id(RESOURCE_ID).name(randomId()).nameTypeId(randomId()).instanceId(randomId()).build();
    var payload = toMap(contributorEventBuilder);
    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, CONTRIBUTOR_RESOURCE, CREATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3).accept(expectedEvents.get(0), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    messageListener.handleContributorEvents(List.of(new ConsumerRecord<>(
      inventoryContributorTopic(), 0, 0, RESOURCE_ID,
      resourceEvent(null, CONTRIBUTOR_RESOURCE, CREATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleConsortiumInstanceEvents_positive() {
    var consortiumInstanceEvent = new ConsortiumInstanceEvent(RESOURCE_ID);
    consortiumInstanceEvent.setTenant(TENANT_ID);
    messageListener.handleConsortiumInstanceEvents(singletonList(new ConsumerRecord<>(
      consortiumInstanceTopic(), 0, 0, RESOURCE_ID, consortiumInstanceEvent
    )));

    verify(resourceService).indexConsortiumInstances(singletonList(consortiumInstanceEvent));
    verify(batchProcessor).consumeBatchWithFallback(eq(singletonList(consortiumInstanceEvent)),
      eq(KAFKA_RETRY_TEMPLATE_NAME),
      any(), any());
  }

  @Test
  void handleConsortiumInstanceEvents_negative() {
    var consortiumInstanceEvent = new ConsortiumInstanceEvent(RESOURCE_ID);
    consortiumInstanceEvent.setTenant(TENANT_ID);
    doAnswer(inv -> {
      inv.<BiConsumer<ConsortiumInstanceEvent, Exception>>getArgument(3)
        .accept(consortiumInstanceEvent, new Exception("error"));
      return null;
    }).when(batchProcessor)
      .consumeBatchWithFallback(eq(singletonList(consortiumInstanceEvent)), eq(KAFKA_RETRY_TEMPLATE_NAME), any(),
        any());

    messageListener.handleConsortiumInstanceEvents(singletonList(new ConsumerRecord<>(
      consortiumInstanceTopic(), 0, 0, RESOURCE_ID, consortiumInstanceEvent
    )));

    verify(batchProcessor).consumeBatchWithFallback(eq(singletonList(consortiumInstanceEvent)),
      eq(KAFKA_RETRY_TEMPLATE_NAME),
      any(), any());
  }

  @Test
  void handleLinkedDataWorkEvent_positive() {
    var payload = toMap(new LinkedDataWork().id(RESOURCE_ID));

    var consumerRecord = new ConsumerRecord<>(linkedDataWorkTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_WORK_RESOURCE, CREATE, payload, null));
    messageListener.handleLinkedDataEvents(List.of(consumerRecord));

    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, LINKED_DATA_WORK_RESOURCE, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataWorkEvent_negative_logFailedEvent() {
    var payload = toMap(new LinkedDataWork().id(RESOURCE_ID));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, LINKED_DATA_WORK_RESOURCE, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3).accept(expectedEvents.get(0), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    var consumerRecord = new ConsumerRecord<>(linkedDataWorkTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_WORK_RESOURCE, UPDATE, payload, null));
    messageListener.handleLinkedDataEvents(List.of(consumerRecord));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataAuthorityEvent_positive() {
    var payload = toMap(new LinkedDataAuthority().id(RESOURCE_ID));

    messageListener.handleLinkedDataEvents(List.of(new ConsumerRecord<>(
      linkedDataAuthorityTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_AUTHORITY_RESOURCE, CREATE, payload, null))));

    var expectedEvents = singletonList(
      resourceEvent(RESOURCE_ID, LINKED_DATA_AUTHORITY_RESOURCE, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataAuthorityEvent_negative_logFailedEvent() {
    var payload = toMap(new LinkedDataAuthority().id(RESOURCE_ID));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, LINKED_DATA_AUTHORITY_RESOURCE, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3).accept(expectedEvents.get(0), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    messageListener.handleLinkedDataEvents(List.of(new ConsumerRecord<>(
      linkedDataAuthorityTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_AUTHORITY_RESOURCE, UPDATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleClassificationTypeEvent_positive_filterOnlyDeleteEvents() {
    var deleteEvent = resourceEvent(RESOURCE_ID, ResourceType.CLASSIFICATION_TYPE.getValue(), DELETE, null, emptyMap());
    var createEvent = resourceEvent(RESOURCE_ID, ResourceType.CLASSIFICATION_TYPE.getValue(), CREATE, emptyMap(), null);
    var updateEvent = resourceEvent(RESOURCE_ID, ResourceType.CLASSIFICATION_TYPE.getValue(), UPDATE, null, null);

    messageListener.handleClassificationTypeEvents(List.of(
      classificationTypeConsumerRecord(deleteEvent),
      classificationTypeConsumerRecord(updateEvent),
      classificationTypeConsumerRecord(createEvent))
    );

    verify(configSynchronizationService).sync(List.of(deleteEvent), ResourceType.CLASSIFICATION_TYPE);
    verify(batchProcessor).consumeBatchWithFallback(eq(List.of(deleteEvent)), any(), any(), any());
  }

  @NotNull
  private static ConsumerRecord<String, ResourceEvent> classificationTypeConsumerRecord(ResourceEvent deleteEvent) {
    return new ConsumerRecord<>(inventoryClassificationTopic(), 0, 0, RESOURCE_ID, deleteEvent);
  }
}
