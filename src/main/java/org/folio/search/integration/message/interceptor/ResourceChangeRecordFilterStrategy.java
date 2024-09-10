package org.folio.search.integration.message.interceptor;

import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceDeleteEventSubType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ResourceChangeRecordFilterStrategy implements RecordFilterStrategy<String, ResourceEvent> {

  @Override
  public boolean filter(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    log.debug("Processing resource event [id: {}]", consumerRecord.value().getId());
    var resourceEvent = consumerRecord.value();
    var resourceName = resourceEvent.getResourceName();
    var resourceEventType = resourceEvent.getType();
    if (ResourceEventType.DELETE == resourceEventType && ResourceType.AUTHORITY.getName().equals(resourceName)
        && ResourceDeleteEventSubType.HARD_DELETE == resourceEvent.getDeleteEventSubType()) {
      log.debug("Skip hard-delete event. No need to process event for authority resource");
      return true;
    }
    return false;
  }
}
