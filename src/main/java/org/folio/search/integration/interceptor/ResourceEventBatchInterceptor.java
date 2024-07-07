package org.folio.search.integration.interceptor;

import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.utils.KafkaUtils;
import org.folio.search.utils.SearchUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.stereotype.Component;

@Component
public class ResourceEventBatchInterceptor implements BatchInterceptor<String, ResourceEvent> {

  private static final Map<String, String> TOPIC_TO_RESOURCE_MAP = Map.ofEntries(
    Map.entry("inventory.instance", SearchUtils.INSTANCE_RESOURCE),
    Map.entry("inventory.holdings-record", SearchUtils.INSTANCE_RESOURCE),
    Map.entry("inventory.item", SearchUtils.INSTANCE_RESOURCE),
    Map.entry("inventory.bound-with", SearchUtils.INSTANCE_RESOURCE),
    Map.entry("authorities.authority", SearchUtils.AUTHORITY_RESOURCE),
    Map.entry("search.instance-contributor", SearchUtils.CONTRIBUTOR_RESOURCE),
    Map.entry("search.instance-subject", SearchUtils.INSTANCE_SUBJECT_RESOURCE),
    Map.entry("inventory.classification-type", SearchUtils.CLASSIFICATION_TYPE_RESOURCE),
    Map.entry("inventory.location", SearchUtils.LOCATION_RESOURCE),
    Map.entry("inventory.campus", SearchUtils.CAMPUS_RESOURCE),
    Map.entry("inventory.institution", SearchUtils.INSTITUTION_RESOURCE),
    Map.entry("inventory.library", SearchUtils.LIBRARY_RESOURCE),
    Map.entry("linked-data.work", SearchUtils.LINKED_DATA_WORK_RESOURCE),
    Map.entry("linked-data.authority", SearchUtils.LINKED_DATA_AUTHORITY_RESOURCE)
  );

  @Override
  public ConsumerRecords<String, ResourceEvent> intercept(@NotNull ConsumerRecords<String, ResourceEvent> records,
                                                          @NotNull Consumer<String, ResourceEvent> consumer) {
    records.forEach(consumerRecord -> {
      var topicName = KafkaUtils.getTopicName(consumerRecord);
      var resourceName = TOPIC_TO_RESOURCE_MAP.getOrDefault(topicName, "unknown");
      consumerRecord.value().id(consumerRecord.key()).resourceName(resourceName);
    });
    return records;
  }
}
