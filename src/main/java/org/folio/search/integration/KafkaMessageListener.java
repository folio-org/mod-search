package org.folio.search.integration;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.ConsortiumInstanceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.utils.KafkaConstants;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A Spring component for consuming events from messaging system.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final ResourceService resourceService;
  private final FolioMessageBatchProcessor folioMessageBatchProcessor;
  private final SystemUserScopedExecutionService executionService;
  private final ConfigSynchronizationService configSynchronizationService;

  /**
   * Handles instance events and indexes them by id.
   *
   * @param consumerRecords - list of consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaConstants.EVENT_LISTENER_ID,
    containerFactory = "instanceResourceListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['events'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['events'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['events'].concurrency}")
  public void handleInstanceEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing instance ids from kafka events [number of events: {}]", consumerRecords.size());
    var batch = getInstanceResourceEvents(consumerRecords);
    var batchByTenant = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));
    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(resourceEvents, KAFKA_RETRY_TEMPLATE_NAME,
        resourceService::indexInstancesById, KafkaMessageListener::logFailedEvent);
      return null;
    }));

  }

  /**
   * Handles authority record events and indexes them using event body.
   *
   * @param consumerRecords - list of consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaConstants.AUTHORITY_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['authorities'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['authorities'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['authorities'].topicPattern}")
  public void handleAuthorityEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing authority events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .filter(authority -> !StringUtils.startsWith(getResourceSource(authority), SOURCE_CONSORTIUM_PREFIX))
      .map(authority -> authority.id(getResourceEventId(authority)))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  @KafkaListener(
    id = KafkaConstants.CLASSIFICATION_TYPE_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['classification-type'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['classification-type'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['classification-type'].topicPattern}")
  @CacheEvict(cacheNames = REFERENCE_DATA_CACHE, allEntries = true)
  public void handleClassificationTypeEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing classification-type events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .filter(resourceEvent -> resourceEvent.getType() == DELETE).toList();

    var batchByTenant = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));

    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
        resourceEvent -> configSynchronizationService.sync(resourceEvent, ResourceType.CLASSIFICATION_TYPE),
        KafkaMessageListener::logFailedEvent);
      return null;
    }));
  }

  @KafkaListener(
    id = KafkaConstants.LOCATION_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['location'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['location'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['location'].topicPattern}")
  public void handleLocationEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing location events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(location -> location.id(getResourceEventId(location) + "|" + location.getTenant()))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  @KafkaListener(
    id = KafkaConstants.LINKED_DATA_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['linked-data'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['linked-data'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['linked-data'].topicPattern}")
  public void handleLinkedDataEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing linked data events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(ld -> ld.id(getResourceEventId(ld)))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  private void indexResources(List<ResourceEvent> batch, Consumer<List<ResourceEvent>> indexConsumer) {
    var batchByTenant = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));

    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(resourceEvents, KAFKA_RETRY_TEMPLATE_NAME,
        indexConsumer, KafkaMessageListener::logFailedEvent);
      return null;
    }));
  }

  private static List<ResourceEvent> getInstanceResourceEvents(List<ConsumerRecord<String, ResourceEvent>> events) {
    return events.stream()
      .map(KafkaMessageListener::getInstanceResourceEvent)
      .filter(Objects::nonNull)
      .distinct()
      .toList();
  }

  private static ResourceEvent getInstanceResourceEvent(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var instanceId = getInstanceId(consumerRecord);
    var value = consumerRecord.value();
    if (instanceId == null) {
      log.warn("Failed to find instance id in record [record: {}]", replaceAll(value.toString(), "\\s+", " "));
      return null;
    }
    var operation = isInstanceResource(consumerRecord) ? value.getType() : CREATE;
    return value.id(instanceId).type(operation);
  }

  private static String getInstanceId(ConsumerRecord<String, ResourceEvent> event) {
    var body = event.value();
    if (body.getType() == REINDEX) {
      return event.key();
    }
    var eventPayload = getEventPayload(body);
    return isInstanceResource(event) ? getString(eventPayload, ID_FIELD) : getString(eventPayload, INSTANCE_ID_FIELD);
  }

  private static boolean isInstanceResource(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    return consumerRecord.topic().endsWith("inventory.instance");
  }

  private static void logFailedEvent(ResourceEvent event, Exception e) {
    if (event == null) {
      log.warn("Failed to index resource event [event: null]", e);
      return;
    }

    var eventType = event.getType() != null ? event.getType().getValue() : "unknown";
    var resourceName = event.getResourceName() != null ? event.getResourceName() : "unknown";
    log.warn(new FormattedMessage(
      "Failed to index resource event [resource: {}, eventType: {}, tenantId: {}, id: {}]",
      resourceName, eventType, event.getTenant(), event.getId()
    ), e);
  }

  private static void logFailedConsortiumEvent(ConsortiumInstanceEvent event, Exception e) {
    if (event == null) {
      log.warn("Failed to index resource event [event: null]", e);
      return;
    }

    log.warn(new FormattedMessage("Failed to index consortium instance [tenantId: {}, id: {}]",
      event.getTenant(), event.getInstanceId()), e);
  }
}
