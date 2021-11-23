package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.IndexService;
import org.folio.search.service.KafkaAdminService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A Spring component for consuming events from messaging system.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final FolioMessageBatchProcessor folioMessageBatchProcessor;
  private final IndexService indexService;

  /**
   * Handles instance events and indexes them by id.
   *
   * @param consumerRecords - list of consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaAdminService.EVENT_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['events'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['events'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['events'].concurrency}")
  public void handleEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing instance ids from kafka events [number of events: {}]", consumerRecords.size());
    var batch = getResourceIdRecords(consumerRecords);
    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      indexService::indexResourcesById, KafkaMessageListener::logFailedEvent);
  }

  /**
   * Handles authority record events and indexes them using event body.
   *
   * @param consumerRecords - list of consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaAdminService.AUTHORITY_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['authorities'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['authorities'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['authorities'].topicPattern}")
  public void handleAuthorityEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing authority events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(authority -> authority.resourceName(AUTHORITY_RESOURCE))
      .collect(toList());

    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      indexService::indexResources, KafkaMessageListener::logFailedEvent);
  }

  private static List<ResourceIdEvent> getResourceIdRecords(List<ConsumerRecord<String, ResourceEvent>> events) {
    return events.stream()
      .map(KafkaMessageListener::getResourceIdRecord)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
  }

  private static ResourceIdEvent getResourceIdRecord(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var instanceId = getInstanceId(consumerRecord);
    var value = consumerRecord.value();
    if (instanceId == null) {
      log.warn("Failed to find instance id in record [record: {}]", replaceAll(value.toString(), "\\n", ""));
      return null;
    }
    var operation = value.getType() == ResourceEventType.DELETE && isInstanceResource(consumerRecord) ? DELETE : INDEX;
    return ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, value.getTenant(), operation);
  }

  private static String getInstanceId(ConsumerRecord<String, ResourceEvent> event) {
    var body = event.value();
    if (body.getType() == REINDEX) {
      return event.key();
    }
    var eventPayload = getEventPayload(body);
    return isInstanceResource(event) ? getString(eventPayload, "id") : getString(eventPayload, "instanceId");
  }

  private static boolean isInstanceResource(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    return consumerRecord.topic().endsWith("inventory." + INSTANCE_RESOURCE);
  }

  private static void logFailedEvent(ResourceIdEvent event, Exception e) {
    log.warn("Failed to index resource event [eventType: {}, type: {}, tenantId: {}, id: {}]",
      event.getAction().getValue(), event.getType(), event.getTenant(), event.getId(), e);
  }

  private static void logFailedEvent(ResourceEvent event, Exception e) {
    log.warn("Failed to index resource event [eventType: {}, type: {}, tenantId: {}, id: {}]",
      event.getType().getValue(), event.getType(), event.getTenant(), event.getId(), e);
  }
}
