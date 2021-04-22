package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.CREATE;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.DELETE;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.REINDEX;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.UPDATE;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
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
  private static final EnumSet<ResourceEventBody.TypeEnum> INDEX_EVENT_TYPES =
    EnumSet.of(CREATE, UPDATE, REINDEX);

  private final IndexService indexService;

  @KafkaListener(
    id = "mod-search-events-listener",
    containerFactory = "kafkaListenerContainerFactory",
    topics = "#{'${application.kafka.listener.events.topics}'.split(',')}",
    groupId = "${application.kafka.listener.events.group-id}",
    concurrency = "${application.kafka.listener.events.concurrency}",
    errorHandler = "kafkaErrorHandler")
  public void handleEvents(List<ConsumerRecord<String, ResourceEventBody>> consumerRecords) {
    log.info("Processing instance ids from kafka events [number of events: {}]",
      consumerRecords.size());

    indexResources(consumerRecords);
    removeResources(consumerRecords);
  }

  private void indexResources(List<ConsumerRecord<String, ResourceEventBody>> events) {
    var resourcesToIndex = getResourceIdRecords(events, this::isIndexEvent);
    indexService.indexResourcesById(resourcesToIndex);
  }

  private void removeResources(List<ConsumerRecord<String, ResourceEventBody>> events) {
    var resourcesToRemove = getResourceIdRecords(events, this::isRemoveEvent);
    indexService.removeResources(resourcesToRemove);
  }

  private List<ResourceIdEvent> getResourceIdRecords(
    List<ConsumerRecord<String, ResourceEventBody>> events,
    Predicate<ConsumerRecord<String, ResourceEventBody>> filter) {

    return events.stream()
      .filter(filter)
      .map(this::getResourceIdRecord)
      .filter(Objects::nonNull)
      .distinct()
      .collect(toList());
  }

  private ResourceIdEvent getResourceIdRecord(ConsumerRecord<String, ResourceEventBody> consumerRecord) {
    var instanceId = getInstanceId(consumerRecord);
    if (instanceId == null) {
      log.warn("Failed to find instance id in record [record: {}]",
        replaceAll(consumerRecord.value().toString(), "\\n", ""));
      return null;
    }
    var tenantId = consumerRecord.value().getTenant();
    return ResourceIdEvent.of(instanceId, INSTANCE_RESOURCE, tenantId);
  }

  private String getInstanceId(ConsumerRecord<String, ResourceEventBody> event) {
    var eventResourceBody = event.value();
    if (eventResourceBody.getType() == REINDEX) {
      return event.key();
    }

    Map<String, Object> eventPayload = eventResourceBody.getNew() != null
      ? getNewAsMap(eventResourceBody) : getOldAsMap(eventResourceBody);

    if (isInstanceResource(event)) {
      return MapUtils.getString(eventPayload, "id");
    }
    return MapUtils.getString(eventPayload, "instanceId");
  }

  private boolean isIndexEvent(ConsumerRecord<String, ResourceEventBody> event) {
    var eventType = event.value().getType();
    if (INDEX_EVENT_TYPES.contains(eventType)) {
      return true;
    }

    // For items or holdings DELETE we just have to re-index associated instance
    return !isInstanceResource(event) && eventType == DELETE;
  }

  private boolean isRemoveEvent(ConsumerRecord<String, ResourceEventBody> event) {
    // remove events for items/holdings is handled as reindex of the instance
    return isInstanceResource(event) && event.value().getType() == DELETE;
  }

  private boolean isInstanceResource(ConsumerRecord<String, ResourceEventBody> consumerRecord) {
    return "inventory.instance".equals(consumerRecord.topic());
  }
}
