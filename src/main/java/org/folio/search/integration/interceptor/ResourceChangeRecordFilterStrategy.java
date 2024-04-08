package org.folio.search.integration.interceptor;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceDeleteEventSubType;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
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
    if (ResourceEventType.DELETE == resourceEventType && AUTHORITY_RESOURCE.equals(resourceName)
      && ResourceDeleteEventSubType.HARD_DELETE == resourceEvent.getDeleteEventSubType()) {
      log.debug("Skip hard-delete event. No need to process event for authority resource");
      return true;
    }
    return false;
  }
}
