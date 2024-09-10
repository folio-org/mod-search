package org.folio.search.integration.message.interceptor;

import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;

import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.utils.KafkaUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.stereotype.Component;

@Order(value = Ordered.LOWEST_PRECEDENCE - 1)
@Component
public class ResourceEventBatchInterceptor implements BatchInterceptor<String, ResourceEvent> {

  private static final Map<String, ResourceType> TOPIC_TO_RESOURCE_MAP = Map.ofEntries(
    Map.entry("inventory.instance", ResourceType.INSTANCE),
    Map.entry("inventory.holdings-record", ResourceType.HOLDINGS),
    Map.entry("inventory.item", ResourceType.ITEM),
    Map.entry("inventory.bound-with", ResourceType.BOUND_WITH),
    Map.entry("authorities.authority", ResourceType.AUTHORITY),
    Map.entry("search.instance-contributor", ResourceType.INSTANCE_CONTRIBUTOR),
    Map.entry("search.instance-subject", ResourceType.INSTANCE_SUBJECT),
    Map.entry("inventory.classification-type", ResourceType.CLASSIFICATION_TYPE),
    Map.entry("inventory.location", ResourceType.LOCATION),
    Map.entry("inventory.campus", ResourceType.CAMPUS),
    Map.entry("inventory.institution", ResourceType.INSTITUTION),
    Map.entry("inventory.library", ResourceType.LIBRARY),
    Map.entry("linked-data.work", ResourceType.LINKED_DATA_WORK),
    Map.entry("linked-data.authority", ResourceType.LINKED_DATA_AUTHORITY)
  );

  @Override
  public ConsumerRecords<String, ResourceEvent> intercept(@NotNull ConsumerRecords<String, ResourceEvent> records,
                                                          @NotNull Consumer<String, ResourceEvent> consumer) {
    records.forEach(consumerRecord -> {
      var topicName = KafkaUtils.getTopicName(consumerRecord);
      var resourceType = TOPIC_TO_RESOURCE_MAP.getOrDefault(topicName, ResourceType.UNKNOWN);
      var id = consumerRecord.key();
      if (resourceType.equals(ResourceType.HOLDINGS) || resourceType.equals(ResourceType.ITEM)) {
        id = getResourceEventId(consumerRecord.value());
      }
      consumerRecord.value().id(id).resourceName(resourceType.getName());
    });
    return records;
  }
}
