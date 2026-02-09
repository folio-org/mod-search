package org.folio.search.integration.message;

import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.configuration.SearchCacheNames.REFERENCE_DATA_CACHE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Strings;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.logging.log4j.message.FormattedMessage;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.ResourceService;
import org.folio.search.service.config.ConfigSynchronizationService;
import org.folio.search.utils.KafkaConstants;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
  private final KafkaTemplate<String, IndexInstanceEvent> instanceEventProducer;
  private final InstanceEventMapper instanceEventMapper;

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
    log.info("Processing instance related events from kafka events [number of events: {}]", consumerRecords.size());
    consumerRecords.stream().collect(Collectors.groupingBy(consumerRecord -> consumerRecord.value().getTenant()))
      .forEach((tenant, records) -> executionService.executeSystemUserScoped(tenant, () -> {
        records.stream()
          .map(instanceEventMapper::mapToProducerRecords)
          .flatMap(List::stream)
          .forEach(instanceEventProducer::send);
        return null;
      }));
  }

  /**
   * Handles instance events and indexes them by id.
   *
   * @param consumerRecords - list of consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaConstants.INDEX_INSTANCE_LISTENER_ID,
    containerFactory = "indexInstanceListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['index-instance'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['index-instance'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['index-instance'].concurrency}")
  public void handleIndexInstanceEvents(List<ConsumerRecord<String, IndexInstanceEvent>> consumerRecords) {
    log.info("Processing index instance events from kafka [number of events: {}]", consumerRecords.size());
    var batchByTenant = consumerRecords.stream().map(ConsumerRecord::value)
      .collect(Collectors.groupingBy(IndexInstanceEvent::tenant));
    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(resourceEvents, KAFKA_RETRY_TEMPLATE_NAME,
        resourceService::indexInstanceEvents, KafkaMessageListener::logFailedEvent);
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
      .filter(authority -> !Strings.CS.startsWith(getResourceSource(authority), SOURCE_CONSORTIUM_PREFIX))
      .map(authority -> authority.id(getResourceEventId(authority)))
      .toList();

    indexResources(batch, resourceService::indexResources);
  }

  @KafkaListener(
    id = KafkaConstants.BROWSE_CONFIG_DATA_LISTENER_ID,
    containerFactory = "resourceListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['browse-config-data'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['browse-config-data'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['browse-config-data'].topicPattern}")
  @CacheEvict(cacheNames = REFERENCE_DATA_CACHE, allEntries = true)
  public void handleBrowseConfigDataEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing browse config data events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .filter(resourceEvent -> resourceEvent.getType() == DELETE)
      .toList();

    var batchByTenant = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));

    batchByTenant.forEach((tenant, resourceEvents) -> executionService.executeSystemUserScoped(tenant, () -> {
      folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
        resourceEvent -> {
          var eventsByResource = resourceEvent.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));
          eventsByResource.forEach((resourceName, events) ->
            configSynchronizationService.sync(resourceEvent, ResourceType.byName(resourceName)));
        },
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
      .filter(Predicate.not(SearchConverterUtils::isShadowLocationOrUnit))
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

  private static void logFailedEvent(IndexInstanceEvent event, Exception e) {
    if (event == null) {
      log.warn("Failed to index resource event [event: null]", e);
      return;
    }

    log.warn(new FormattedMessage(
      "Failed to index instance event [tenantId: {}, id: {}]",
      event.tenant(), event.instanceId()
    ), e);
  }
}
