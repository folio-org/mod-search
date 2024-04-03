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

  private static final Map<String, String> TOPIC_TO_RESOURCE_MAP = Map.of(
    "inventory.instance", SearchUtils.INSTANCE_RESOURCE,
    "inventory.holdings-record", SearchUtils.INSTANCE_RESOURCE,
    "inventory.item", SearchUtils.INSTANCE_RESOURCE,
    "inventory.bound-with", SearchUtils.INSTANCE_RESOURCE,
    "authorities.authority", SearchUtils.AUTHORITY_RESOURCE,
    "search.instance-contributor", SearchUtils.CONTRIBUTOR_RESOURCE,
    "search.instance-subject", SearchUtils.INSTANCE_SUBJECT_RESOURCE,
    "inventory.classification-type", SearchUtils.CLASSIFICATION_TYPE_RESOURCE,
    "inventory.location", SearchUtils.LOCATION_RESOURCE
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
