package org.folio.search.integration;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.RegExUtils.replaceAll;
import static org.folio.search.domain.dto.ResourceEventBody.TypeEnum.REINDEX;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

  public static final String INVENTORY_INSTANCE_TOPIC = "inventory.instance";
  private final IndexService indexService;

  @KafkaListener(
    id = KafkaAdminService.EVENT_LISTENER_ID,
    containerFactory = "kafkaListenerContainerFactory",
    topicPattern = "#{folioKafkaProperties.listener['events'].topicPattern}",
    groupId = "#{folioKafkaProperties.listener['events'].groupId}",
    concurrency = "#{folioKafkaProperties.listener['events'].concurrency}",
    errorHandler = "kafkaErrorHandler")
  public void handleEvents(List<ConsumerRecord<String, ResourceEventBody>> consumerRecords) {
    log.info("Processing instance ids from kafka events [number of events: {}]", consumerRecords.size());
    indexService.indexResourcesById(getResourceIdRecords(consumerRecords));
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
    var eventPayload = body.getNew() != null ? getNewAsMap(body) : getOldAsMap(body);
    return isInstanceResource(event) ? getString(eventPayload, "id") : getString(eventPayload, "instanceId");
  }

  private boolean isInstanceResource(ConsumerRecord<String, ResourceEventBody> consumerRecord) {
    return consumerRecord.topic().endsWith(INVENTORY_INSTANCE_TOPIC);
  }
}
