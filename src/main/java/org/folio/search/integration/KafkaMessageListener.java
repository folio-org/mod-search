package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.configuration.KafkaConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.REINDEX;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getResourceName;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.domain.dto.ResourceEventBody.TypeEnum;
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
  public void handleEvents(List<ConsumerRecord<String, ResourceEventBody>> consumerRecords) {
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
  public void handleAuthorityEvents(List<ConsumerRecord<String, ResourceEventBody>> consumerRecords) {
    log.info("Processing authority events from Kafka [number of events: {}]", consumerRecords.size());
    var resourceName = getResourceName(Authority.class);
    List<ResourceEventBody> batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(authority -> authority.id(getString(getEventPayload(authority), "id")).resourceName(resourceName))
      .collect(toList());

    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      indexService::indexResources, KafkaMessageListener::logFailedEvent);
  }

  private List<ResourceIdEvent> getResourceIdRecords(List<ConsumerRecord<String, ResourceEventBody>> events) {
    return events.stream()
      .map(this::getResourceIdRecord)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
  }

  private ResourceIdEvent getResourceIdRecord(ConsumerRecord<String, ResourceEventBody> consumerRecord) {
    var instanceId = getInstanceId(consumerRecord);
    var value = consumerRecord.value();
    if (instanceId == null) {
      log.warn("Failed to find instance id in record [record: {}]", replaceAll(value.toString(), "\\n", ""));
      return null;
    }
    var operation = value.getType() == TypeEnum.DELETE && isInstanceResource(consumerRecord) ? DELETE : INDEX;
    return ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, value.getTenant(), operation);
  }

  private String getInstanceId(ConsumerRecord<String, ResourceEventBody> event) {
    var body = event.value();
    if (body.getType() == REINDEX) {
      return event.key();
    }
    var eventPayload = getEventPayload(body);
    return isInstanceResource(event) ? getString(eventPayload, "id") : getString(eventPayload, "instanceId");
  }

  private static Map<String, Object> getEventPayload(ResourceEventBody body) {
    return body.getNew() != null ? getNewAsMap(body) : getOldAsMap(body);
  }

  private boolean isInstanceResource(ConsumerRecord<String, ResourceEventBody> consumerRecord) {
    return consumerRecord.topic().endsWith("inventory." + INSTANCE_RESOURCE);
  }

  private static void logFailedEvent(ResourceIdEvent event, Exception e) {
    log.warn("Failed to index resource event [eventType: {}, type: {}, tenantId: {}, id: {}]",
      event.getAction().getValue(), event.getType(), event.getTenant(), event.getId(), e);
  }

  private static void logFailedEvent(ResourceEventBody event, Exception e) {
    log.warn("Failed to index resource event [eventType: {}, type: {}, tenantId: {}, id: {}]",
      event.getType().getValue(), event.getType(), event.getTenant(), event.getId(), e);
  }
}
