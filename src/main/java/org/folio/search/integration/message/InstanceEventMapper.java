package org.folio.search.integration.message;

import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.INDEX_INSTANCE;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.springframework.stereotype.Component;

/**
 * Maps instance resource events to index instance events with producer records.
 */
@Component
@RequiredArgsConstructor
public class InstanceEventMapper {

  private final ConsortiumTenantService consortiumTenantService;

  /**
   * Maps a consumer record to a producer record for indexing.
   *
   * @param event the consumer record containing resource event
   * @return producer record ready to be sent to Kafka
   */
  public List<ProducerRecord<String, IndexInstanceEvent>> mapToProducerRecords(
    ConsumerRecord<String, ResourceEvent> event) {
    if (isInstanceResource(event.topic())) {
      var producerRecord = toProducerRecord(event.value(), event.headers(), event.topic());
      return List.of(producerRecord);
    } else {
      return extractEventsForDataMove(event.value()).stream()
        .map(resourceEvent -> toProducerRecord(resourceEvent, event.headers(), event.topic()))
        .toList();
    }
  }

  private ProducerRecord<String, IndexInstanceEvent> toProducerRecord(ResourceEvent resourceEvent, Headers headers,
                                                                      String topic) {
    var instanceId = extractInstanceId(resourceEvent, isInstanceResource(topic));
    var eventTenant = resourceEvent.getTenant();
    var targetTenant = consortiumTenantService.getCentralTenant(eventTenant).orElse(eventTenant);
    var indexInstanceEvent = new IndexInstanceEvent(targetTenant, instanceId);

    return new ProducerRecordBuilder<>(getFullTopicName(targetTenant), instanceId, indexInstanceEvent, headers)
      .withUpdatedTenantHeaders(targetTenant);
  }

  private String getFullTopicName(String targetTenant) {
    return INDEX_INSTANCE.fullTopicName(targetTenant);
  }

  private String extractInstanceId(ResourceEvent body, boolean instanceResource) {
    var eventPayload = getEventPayload(body);
    return instanceResource
           ? MapUtils.getString(eventPayload, ID_FIELD)
           : MapUtils.getString(eventPayload, INSTANCE_ID_FIELD);
  }

  private boolean isInstanceResource(String topic) {
    return topic.endsWith("inventory.instance");
  }

  /**
   * There may be a case when some data is moved between instances.
   * In such case old and new fields of the event will have different instanceId.
   * This method will create 2 events out of 1 and erase 'old' field in an original event.
   */
  private List<ResourceEvent> extractEventsForDataMove(ResourceEvent resourceEvent) {
    if (resourceEvent == null) {
      return List.of();
    }

    var oldMap = getOldAsMap(resourceEvent);
    var newMap = getNewAsMap(resourceEvent);
    var oldInstanceId = oldMap.get(INSTANCE_ID_FIELD);

    if (oldInstanceId != null && !oldInstanceId.equals(newMap.get(INSTANCE_ID_FIELD))) {
      var oldEvent = new ResourceEvent().id(String.valueOf(oldInstanceId))
        .resourceName(resourceEvent.getResourceName())
        .type(resourceEvent.getType())
        .tenant(resourceEvent.getTenant())
        ._new(resourceEvent.getOld());
      var newEvent = resourceEvent.old(null);
      return List.of(oldEvent, newEvent);
    }
    return List.of(resourceEvent);
  }
}
