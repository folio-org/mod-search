package org.folio.search.integration.message;

import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.INDEX_INSTANCE;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.types.ResourceType;
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
    var resourceEvent = event.value();
    var eventTenant = resourceEvent.getTenant();
    var targetTenant = consortiumTenantService.getCentralTenant(eventTenant).orElse(eventTenant);
    if (isInstanceResource(resourceEvent)) {
      var instanceId = MapUtils.getString(getEventPayload(resourceEvent), ID_FIELD);
      return List.of(toProducerRecord(instanceId, targetTenant, event.headers()));
    } else {
      var oldInstanceId = getInstanceId(getOldAsMap(resourceEvent));
      var newInstanceId = getInstanceId(getNewAsMap(resourceEvent));
      if (oldInstanceId != null && !oldInstanceId.equals(newInstanceId)) {
        return List.of(toProducerRecord(oldInstanceId, targetTenant, event.headers()),
          toProducerRecord(oldInstanceId, targetTenant, event.headers()));
      }
      return List.of(toProducerRecord(newInstanceId, targetTenant, event.headers()));
    }
  }

  private String getInstanceId(Map<String, Object> oldMap) {
    return MapUtils.getString(oldMap, INSTANCE_ID_FIELD);
  }

  private boolean isInstanceResource(ResourceEvent resourceEvent) {
    return ResourceType.byName(resourceEvent.getResourceName()).equals(ResourceType.INSTANCE);
  }

  private ProducerRecord<String, IndexInstanceEvent> toProducerRecord(String instanceId, String targetTenant,
                                                                      Headers headers) {
    var topic = getFullTopicName(targetTenant);
    var value = new IndexInstanceEvent(targetTenant, instanceId);

    return new ProducerRecordBuilder<>(topic, instanceId, value, headers).withUpdatedTenantHeaders(targetTenant);
  }

  private String getFullTopicName(String targetTenant) {
    return INDEX_INSTANCE.fullTopicName(targetTenant);
  }
}
