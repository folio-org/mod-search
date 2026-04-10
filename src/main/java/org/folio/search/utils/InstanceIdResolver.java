package org.folio.search.utils;

import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.domain.dto.ResourceEventType.REINDEX;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.search.domain.dto.ResourceEvent;

public final class InstanceIdResolver {

  private InstanceIdResolver() {
  }

  public static String resolve(ConsumerRecord<String, ResourceEvent> record) {
    var body = record.value();
    if (body.getType() == REINDEX) {
      return record.key();
    }
    var eventPayload = getEventPayload(body);
    return isInstanceResource(record)
      ? getString(eventPayload, ID_FIELD)
      : getString(eventPayload, INSTANCE_ID_FIELD);
  }

  public static boolean isInstanceResource(ConsumerRecord<String, ResourceEvent> record) {
    return record.topic().endsWith("inventory.instance");
  }
}
