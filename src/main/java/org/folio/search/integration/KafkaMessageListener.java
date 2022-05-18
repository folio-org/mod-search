package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.codec.digest.DigestUtils.sha1Hex;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.apache.commons.lang3.StringUtils.toRootLowerCase;
import static org.folio.search.configuration.RetryTemplateConfiguration.KAFKA_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.KafkaUtils.getTenantTopicName;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_RESOURCE;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_CONTRIBUTORS_FIELD_NAME;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.Contributor;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.ContributorEvent;
import org.folio.search.service.KafkaAdminService;
import org.folio.search.service.ResourceService;
import org.folio.search.utils.JsonConverter;
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

  private static final String INSTANCE_CONTRIBUTOR_TOPIC_NAME = "search.instance-contributor";

  private final JsonConverter jsonConverter;
  private final ResourceService resourceService;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final FolioMessageBatchProcessor folioMessageBatchProcessor;

  private static List<ResourceEvent> getInstanceResourceEvents(List<ConsumerRecord<String, ResourceEvent>> events) {
    return events.stream()
      .map(KafkaMessageListener::getInstanceResourceEvent)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
  }

  private static ResourceEvent getInstanceResourceEvent(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var instanceId = getInstanceId(consumerRecord);
    var value = consumerRecord.value();
    if (instanceId == null) {
      log.warn("Failed to find instance id in record [record: {}]", replaceAll(value.toString(), "\\s+", " "));
      return null;
    }
    var operation = isInstanceResource(consumerRecord) ? value.getType() : CREATE;
    return value.id(instanceId).type(operation).resourceName(INSTANCE_RESOURCE);
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
    return consumerRecord.topic().endsWith("inventory." + INSTANCE_RESOURCE);
  }

  private static void logFailedEvent(ResourceEvent event, Exception e) {
    log.warn("Failed to index resource event [eventType: {}, type: {}, tenantId: {}, id: {}]",
      event.getType().getValue(), event.getType(), event.getTenant(), event.getId(), e);
  }

  private static String getContributorId(String tenantId, Contributor contributor) {
    return sha1Hex(
      tenantId + "|" + contributor.getContributorNameTypeId() + "|" + toRootLowerCase(contributor.getName()));
  }

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
    var batch = getInstanceResourceEvents(consumerRecords);
    batch.forEach(this::sendContributorEventsToKafka);
    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      resourceService::indexResourcesById, KafkaMessageListener::logFailedEvent);
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
      .map(authority -> authority.resourceName(AUTHORITY_RESOURCE).id(getResourceEventId(authority)))
      .collect(toList());

    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      resourceService::indexResources, KafkaMessageListener::logFailedEvent);
  }

  /**
   * Handles authority record events and indexes them using event body.
   *
   * @param consumerRecords - list of consumer records from Apache Kafka to process.
   */
  @KafkaListener(
    id = KafkaAdminService.CONTRIBUTOR_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{folioKafkaProperties.listener['contributors'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['contributors'].concurrency}",
    topicPattern = "#{folioKafkaProperties.listener['contributors'].topicPattern}")
  public void handleContributorEvents(List<ConsumerRecord<String, ResourceEvent>> consumerRecords) {
    log.info("Processing contributor events from Kafka [number of events: {}]", consumerRecords.size());
    var batch = consumerRecords.stream()
      .map(ConsumerRecord::value)
      .map(contributor -> contributor.resourceName(CONTRIBUTOR_RESOURCE).id(getResourceEventId(contributor)))
      .collect(toList());

    folioMessageBatchProcessor.consumeBatchWithFallback(batch, KAFKA_RETRY_TEMPLATE_NAME,
      resourceService::indexResources, KafkaMessageListener::logFailedEvent);
  }

  private void sendContributorEventsToKafka(ResourceEvent event) {
    var type = new TypeReference<List<Contributor>>() { };
    var oldContributors = getContributors(getOldAsMap(event), type);
    var newContributors = getContributors(getNewAsMap(event), type);

    sendContributorEventsToKafka(event, subtract(newContributors, oldContributors), CREATE);
    sendContributorEventsToKafka(event, subtract(oldContributors, newContributors), DELETE);
  }

  private List<Contributor> getContributors(Map<String, Object> objectMap, TypeReference<List<Contributor>> type) {
    return jsonConverter.convert(getObject(objectMap, INSTANCE_CONTRIBUTORS_FIELD_NAME, emptyList()), type);
  }

  private void sendContributorEventsToKafka(ResourceEvent evt, Set<Contributor> contributors, ResourceEventType type) {
    var tenantId = evt.getTenant();
    var topicName = getTenantTopicName(INSTANCE_CONTRIBUTOR_TOPIC_NAME, tenantId);
    var instanceId = getResourceEventId(evt);
    contributors.stream()
      .map(contributor -> prepareResourceEvent(contributor, instanceId, type, tenantId))
      .forEach(resourceEvent -> kafkaTemplate.send(topicName, resourceEvent.getId(), resourceEvent));
  }

  private ResourceEvent prepareResourceEvent(Contributor contributor, String instanceId, ResourceEventType type,
                                             String tenantId) {
    var contributorEvent = ContributorEvent.builder()
      .id(getContributorId(tenantId, contributor))
      .instanceId(instanceId)
      .name(contributor.getName())
      .nameTypeId(contributor.getContributorNameTypeId())
      .typeId(contributor.getContributorTypeId())
      .build();
    var eventBody = new ResourceEvent().type(type).tenant(tenantId);
    return type == CREATE ? eventBody._new(contributorEvent) : eventBody.old(contributorEvent);
  }
}
