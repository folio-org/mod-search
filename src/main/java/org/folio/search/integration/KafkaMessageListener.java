package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.IndexService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * A Spring component for consuming events from messaging system.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final IndexService indexService;
  private final ResourceFetchService resourceFetchService;

  /**
   * Listens the events with instance data inside from messaging system and sends request to index this values in search
   * engine.
   *
   * @param events list with the body as json from messaging system.
   */
  @KafkaListener(
    id = "mod-search-instance-listener",
    containerFactory = "kafkaListenerContainerFactory",
    topics = "#{'${application.kafka.listener.instances.topics}'.split(',')}",
    groupId = "${application.kafka.listener.instances.group-id}",
    concurrency = "${application.kafka.listener.instances.concurrency}")
  public void handleInstanceEvents(List<ResourceEventBody> events) {
    log.info("Processing instance events from kafka [number of events: {}]", events.size());
    var resources = events.stream()
      .map(KafkaMessageListener::asInstanceResource)
      .collect(toList());

    indexService.indexResources(resources);
  }

  @KafkaListener(
    id = "mod-search-events-listener",
    containerFactory = "kafkaListenerContainerFactory",
    topics = "#{'${application.kafka.listener.events.topics}'.split(',')}",
    groupId = "${application.kafka.listener.events.group-id}",
    concurrency = "${application.kafka.listener.events.concurrency}")
  public void handleEvents(List<ConsumerRecord<String, ResourceEventBody>> consumerRecords) {
    log.info("Processing instance ids from kafka events [number of events: {}]", consumerRecords.size());
    var resourceIds = consumerRecords.stream()
      .map(this::getResourceIdRecord)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());

    var instancesByIds = resourceFetchService.fetchInstancesByIds(resourceIds);
    indexService.indexResources(instancesByIds);
  }

  private ResourceIdEvent getResourceIdRecord(ConsumerRecord<String, ResourceEventBody> consumerRecord) {
    var instanceId = getInstanceId(consumerRecord);
    if (instanceId == null) {
      log.debug("Failed to find instance id in record [record: {}]",
        replaceAll(consumerRecord.value().toString(), "\\n", ""));
      return null;
    }
    var tenantId = consumerRecord.value().getTenant();
    return ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, tenantId);
  }

  @SuppressWarnings("unchecked")
  private String getInstanceId(ConsumerRecord<String, ResourceEventBody> event) {
    var topic = event.topic();
    var eventResourceBody = event.value();
    if (topic.equals("inventory.instance")) {
      return MapUtils.getString((Map<String, Object>) eventResourceBody.getNew(), "id");
    }
    return MapUtils.getString((Map<String, Object>) eventResourceBody.getNew(), "instanceId");
  }

  private static ResourceEventBody asInstanceResource(ResourceEventBody eventBody) {
    eventBody.setResourceName(INSTANCE_RESOURCE);
    return eventBody;
  }
}
