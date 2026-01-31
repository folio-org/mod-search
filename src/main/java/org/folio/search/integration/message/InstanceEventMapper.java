package org.folio.search.integration.message;

import static org.folio.search.configuration.kafka.KafkaConfiguration.SearchTopic.INDEX_INSTANCE;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;

import org.apache.commons.collections4.MapUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.springframework.stereotype.Component;

/**
 * Maps instance resource events to index instance events with producer records.
 */
@Component
public class InstanceEventMapper {

  private final ConsortiumTenantService consortiumTenantService;

  public InstanceEventMapper(ConsortiumTenantService consortiumTenantService) {
    this.consortiumTenantService = consortiumTenantService;
  }

  /**
   * Maps a consumer record to a producer record for indexing.
   *
   * @param event the consumer record containing resource event
   * @return producer record ready to be sent to Kafka
   */
  public ProducerRecord<String, IndexInstanceEvent> mapToProducerRecord(ConsumerRecord<String, ResourceEvent> event) {
    var instanceId = extractInstanceId(event);
    var eventTenant = event.value().getTenant();
    var targetTenant = consortiumTenantService.getCentralTenant(eventTenant).orElse(eventTenant);
    var indexInstanceEvent = new IndexInstanceEvent(targetTenant, instanceId);

    return new ProducerRecordBuilder<>(
      INDEX_INSTANCE.fullTopicName(targetTenant),
      instanceId,
      indexInstanceEvent,
      event.headers())
      .withUpdatedTenantHeaders(targetTenant);
  }

  private String extractInstanceId(ConsumerRecord<String, ResourceEvent> event) {
    var body = event.value();
    if (body.getType() == ResourceEventType.REINDEX) {
      return event.key();
    }
    var eventPayload = getEventPayload(body);
    return isInstanceResource(event)
           ? MapUtils.getString(eventPayload, ID_FIELD)
           : MapUtils.getString(eventPayload, INSTANCE_ID_FIELD);
  }

  private boolean isInstanceResource(ConsumerRecord<String, ResourceEvent> event) {
    return event.topic().endsWith("inventory.instance");
  }
}
