package org.folio.search.integration;

import static org.folio.search.utils.SearchUtils.AUTHORITY_RESOURCE;

import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventSubType;
import org.folio.search.domain.dto.ResourceEventType;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

@Log4j2
public class ResourceChangeFilterStrategy implements RecordFilterStrategy<String, ResourceEvent> {

  @Override
  public boolean filter(ConsumerRecord<String, ResourceEvent> consumerRecord) {
    log.info("Processing resource event [id: {}]", consumerRecord.value().getId());
    var resourceEvent = consumerRecord.value();
    var resourceName = resourceEvent.getResourceName();
    var resourceEventType = resourceEvent.getType();
    if (resourceEventType == ResourceEventType.DELETE) {
      log.info("Processing resource event [resourceName: {}]", resourceName);
      if (resourceName != null && resourceName.equals(AUTHORITY_RESOURCE)
        && resourceEvent.getSubType() == ResourceEventSubType.HARD_DELETE) {
        log.info("Skip event. No need to process event for authority resource");
        return true;
      }
    }
    return false;
  }
}
