package org.folio.search.integration.interceptor;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.PrimaryResourceRepository;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteAllRecordFilterStrategy implements RecordFilterStrategy<String, ResourceEvent> {

  private final PrimaryResourceRepository primaryResourceRepository;

  @Override
  public boolean filter(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    var resourceEvent = consumerRecord.value();
    if (ResourceEventType.DELETE_ALL == resourceEvent.getType()) {
      var resourceType = ResourceType.byName(resourceEvent.getResourceName());
      primaryResourceRepository.deleteResourceByTenantId(resourceType, resourceEvent.getTenant());
      return true;
    }
    return false;
  }

}
