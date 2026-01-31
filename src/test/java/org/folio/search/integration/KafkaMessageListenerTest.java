package org.folio.search.integration;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.model.types.ResourceType.AUTHORITY;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_HUB;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_INSTANCE;
import static org.folio.search.model.types.ResourceType.LINKED_DATA_WORK;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.inventoryAuthorityTopic;
import static org.folio.support.TestConstants.inventoryBoundWithTopic;
import static org.folio.support.TestConstants.inventoryCallNumberTopic;
import static org.folio.support.TestConstants.inventoryClassificationTopic;
import static org.folio.support.TestConstants.inventoryHoldingTopic;
import static org.folio.support.TestConstants.inventoryInstanceTopic;
import static org.folio.support.TestConstants.inventoryItemTopic;
import static org.folio.support.TestConstants.linkedDataHubTopic;
import static org.folio.support.TestConstants.linkedDataInstanceTopic;
import static org.folio.support.TestConstants.linkedDataWorkTopic;
import static org.folio.support.utils.JsonTestUtils.OBJECT_MAPPER;
import static org.folio.support.utils.JsonTestUtils.toMap;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
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
import org.folio.search.domain.dto.LinkedDataHub;
import org.folio.search.domain.dto.LinkedDataInstance;
import org.folio.search.domain.dto.LinkedDataWork;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.integration.message.KafkaMessageListener;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    lenient().doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).executeSystemUserScoped(any(), any());
  }

  @Test
  void handleIndexInstanceEvents_positive() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var event1 = new IndexInstanceEvent(TENANT_ID, instanceId1);
    var event2 = new IndexInstanceEvent(TENANT_ID, instanceId2);

    messageListener.handleIndexInstanceEvents(List.of(
      new ConsumerRecord<>("test-topic", 0, 0, instanceId1, event1),
      new ConsumerRecord<>("test-topic", 0, 1, instanceId2, event2)
    ));

    var expectedEvents = List.of(event1, event2);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
    verify(resourceService).indexInstanceEvents(expectedEvents);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "UPDATE", "REINDEX", "DELETE"})
  void handleIndexInstanceEvents_allEventTypes(String eventType) {
    var event = new IndexInstanceEvent(TENANT_ID, RESOURCE_ID);

    messageListener.handleIndexInstanceEvents(List.of(
      new ConsumerRecord<>("test-topic", 0, 0, RESOURCE_ID, event)));

    var expectedEvents = List.of(event);
    verify(resourceService).indexInstanceEvents(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleIndexInstanceEvents_negative_shouldLogFailedEvent() {
    var event = new IndexInstanceEvent(TENANT_ID, RESOURCE_ID);
    when(resourceService.indexInstanceEvents(List.of(event))).thenThrow(new RuntimeException("failed to save"));

    messageListener.handleIndexInstanceEvents(List.of(
      new ConsumerRecord<>("test-topic", 0, 0, RESOURCE_ID, event)));
    
    verify(resourceService, times(3)).indexInstanceEvents(List.of(event));
  }

  @Test
  void handleIndexInstanceEvents_positive_reindexEvent() {
    var event = new IndexInstanceEvent(TENANT_ID, RESOURCE_ID);

    messageListener.handleIndexInstanceEvents(List.of(
      new ConsumerRecord<>("test-topic", 0, 0, RESOURCE_ID, event)));

    var expectedEvents = List.of(event);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
    verify(resourceService).indexInstanceEvents(expectedEvents);
  }

  @Test
  void handleIndexInstanceEvents_positive_multipleInstancesFromDifferentSources() {
    var instanceId1 = randomId();
    var instanceId2 = randomId();
    var event1 = new IndexInstanceEvent(TENANT_ID, instanceId1);
    var event2 = new IndexInstanceEvent(TENANT_ID, instanceId2);

    messageListener.handleIndexInstanceEvents(List.of(
      new ConsumerRecord<>("test-topic", 0, 0, instanceId1, event1),
      new ConsumerRecord<>("test-topic", 0, 1, instanceId2, event2)
    ));

    var expectedEvents = List.of(event1, event2);
    verify(resourceService).indexInstanceEvents(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_positive() {
    var payload = toMap(new Authority().id(RESOURCE_ID));

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, AUTHORITY, CREATE, payload, null))));

    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, AUTHORITY, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleAuthorityEvent_positive_shouldSkipAuthorityShadowCopies() {
    var payload = toMap(new Authority().id(RESOURCE_ID).source("CONSORTIUM-MARC"));

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, AUTHORITY, REINDEX, payload, null))));

    verify(resourceService, never()).indexResources(anyList());
    verify(batchProcessor, never()).consumeBatchWithFallback(any(), any(), any(), any());
  }

  @Test
  void handleAuthorityEvent_negative_logFailedEvent() {
    var payload = toMap(new Authority().id(RESOURCE_ID).personalName("test"));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, AUTHORITY, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3)
        .accept(expectedEvents.getFirst(), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    messageListener.handleAuthorityEvents(List.of(new ConsumerRecord<>(
      inventoryAuthorityTopic(), 0, 0, RESOURCE_ID, resourceEvent(null, AUTHORITY, UPDATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataInstanceEvent_positive() {
    var payload = toMap(new LinkedDataInstance().id(RESOURCE_ID));

    var consumerRecord = new ConsumerRecord<>(linkedDataInstanceTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_INSTANCE, CREATE, payload, null));
    messageListener.handleLinkedDataEvents(List.of(consumerRecord));

    var expectedEvents = singletonList(
      resourceEvent(RESOURCE_ID, LINKED_DATA_INSTANCE, CREATE, payload, null)
    );
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataInstanceEvent_negative_logFailedEvent() {
    var payload = toMap(new LinkedDataInstance().id(RESOURCE_ID));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, LINKED_DATA_INSTANCE, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3)
        .accept(expectedEvents.getFirst(), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    var consumerRecord = new ConsumerRecord<>(linkedDataInstanceTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_INSTANCE, UPDATE, payload, null));
    messageListener.handleLinkedDataEvents(List.of(consumerRecord));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataWorkEvent_positive() {
    var payload = toMap(new LinkedDataWork().id(RESOURCE_ID));

    var consumerRecord = new ConsumerRecord<>(linkedDataWorkTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_WORK, CREATE, payload, null));
    messageListener.handleLinkedDataEvents(List.of(consumerRecord));

    var expectedEvents = singletonList(resourceEvent(RESOURCE_ID, LINKED_DATA_WORK, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataWorkEvent_negative_logFailedEvent() {
    var payload = toMap(new LinkedDataWork().id(RESOURCE_ID));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, LINKED_DATA_WORK, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3)
        .accept(expectedEvents.getFirst(), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    var consumerRecord = new ConsumerRecord<>(linkedDataWorkTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_WORK, UPDATE, payload, null));
    messageListener.handleLinkedDataEvents(List.of(consumerRecord));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataHubEvent_positive() {
    var payload = toMap(new LinkedDataHub().id(RESOURCE_ID));

    messageListener.handleLinkedDataEvents(List.of(new ConsumerRecord<>(
      linkedDataHubTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_HUB, CREATE, payload, null))));

    var expectedEvents = singletonList(
      resourceEvent(RESOURCE_ID, LINKED_DATA_HUB, CREATE, payload, null));
    verify(resourceService).indexResources(expectedEvents);
    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @Test
  void handleLinkedDataHubEvent_negative_logFailedEvent() {
    var payload = toMap(new LinkedDataHub().id(RESOURCE_ID));
    var expectedEvents = List.of(resourceEvent(RESOURCE_ID, LINKED_DATA_HUB, UPDATE, payload, null));

    doAnswer(inv -> {
      inv.<BiConsumer<ResourceEvent, Exception>>getArgument(3)
        .accept(expectedEvents.getFirst(), new Exception("error"));
      return null;
    }).when(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());

    messageListener.handleLinkedDataEvents(List.of(new ConsumerRecord<>(
      linkedDataHubTopic(TENANT_ID), 0, 0, RESOURCE_ID,
      resourceEvent(null, LINKED_DATA_HUB, UPDATE, payload, null))));

    verify(batchProcessor).consumeBatchWithFallback(eq(expectedEvents), eq(KAFKA_RETRY_TEMPLATE_NAME), any(), any());
  }

  @ParameterizedTest
  @EnumSource(value = ResourceType.class, names = {"CLASSIFICATION_TYPE", "CALL_NUMBER_TYPE"})
  void handleBrowseConfigDataEvent_positive_filterOnlyDeleteEvents(ResourceType type) {
    var deleteEvent = resourceEvent(RESOURCE_ID, type, DELETE, null, emptyMap());
    var createEvent = resourceEvent(RESOURCE_ID, type, CREATE, emptyMap(), null);
    var updateEvent = resourceEvent(RESOURCE_ID, type, UPDATE, null, null);

    messageListener.handleBrowseConfigDataEvents(List.of(
      consumerRecordForType(type, deleteEvent),
      consumerRecordForType(type, updateEvent),
      consumerRecordForType(type, createEvent))
    );

    verify(configSynchronizationService).sync(List.of(deleteEvent), type);
    verify(batchProcessor).consumeBatchWithFallback(eq(List.of(deleteEvent)), any(), any(), any());
  }

  private static ConsumerRecord<String, ResourceEvent> boundWithRecord(String instanceId1,
                                                                       ResourceEvent boundWithEvent) {
    return new ConsumerRecord<>(inventoryBoundWithTopic(), 0, 0, instanceId1, boundWithEvent);
  }

  private static ConsumerRecord<String, ResourceEvent> holdingRecord(String instanceId3,
                                                                     ResourceEvent holdingEvent) {
    return new ConsumerRecord<>(inventoryHoldingTopic(), 0, 0, instanceId3, holdingEvent);
  }

  private static ConsumerRecord<String, ResourceEvent> itemRecord(String instanceId2, ResourceEvent itemEvent) {
    return new ConsumerRecord<>(inventoryItemTopic(), 0, 0, instanceId2, itemEvent);
  }

  private static ConsumerRecord<String, ResourceEvent> instanceRecord(String instanceId1,
                                                                      ResourceEvent instanceEvent) {
    return new ConsumerRecord<>(inventoryInstanceTopic(), 0, 0, instanceId1, instanceEvent);
  }

  private static ConsumerRecord<String, ResourceEvent> consumerRecordForType(ResourceType resourceType,
                                                                             ResourceEvent event) {
    var topic = switch (resourceType) {
      case CLASSIFICATION_TYPE -> inventoryClassificationTopic();
      case CALL_NUMBER_TYPE -> inventoryCallNumberTopic();
      default -> EMPTY;
    };
    return new ConsumerRecord<>(topic, 0, 0, RESOURCE_ID, event);
  }
}
