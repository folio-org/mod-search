package org.folio.search.integration.interceptor;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;

import jakarta.annotation.Priority;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.search.utils.SearchConverterUtils;
import org.springframework.core.Ordered;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.stereotype.Component;

@Priority(Ordered.LOWEST_PRECEDENCE)
@Component
public class PopulateInstanceBatchInterceptor implements BatchInterceptor<String, ResourceEvent> {

  private final Map<ReindexEntityType, ReindexJdbcRepository> repositories;

  public PopulateInstanceBatchInterceptor(List<ReindexJdbcRepository> repositories) {
    this.repositories = repositories.stream().collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity()));
  }

  @Override
  public ConsumerRecords<String, ResourceEvent> intercept(ConsumerRecords<String, ResourceEvent> records,
                                                          Consumer<String, ResourceEvent> consumer) {
    var recordsById = StreamSupport.stream(records.spliterator(), false)
      .filter(r -> isInstanceEvent(r.value()))
      .collect(Collectors.groupingBy(ConsumerRecord::key));

    List<ResourceEvent> consumerRecords = new ArrayList<>();
    for (var entry : recordsById.entrySet()) {
      var list = entry.getValue();
      if (list.size() > 1) {
        list.sort(Comparator.comparingLong(ConsumerRecord::timestamp));
        consumerRecords.add(list.get(0).value());
      }
    }
    populate(consumerRecords);
    return records;
  }

  private void populate(List<ResourceEvent> records) {
    var recordByResource = records.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));
    for (Map.Entry<String, List<ResourceEvent>> recordCollection : recordByResource.entrySet()) {
      var repository = repositories.get(ReindexEntityType.valueOf(recordCollection.getKey()));
      if (repository != null) {
        var recordByOperation = recordCollection.getValue().stream()
            .collect(Collectors.groupingBy(resourceEvent -> resourceEvent.getType() != ResourceEventType.DELETE));
        var resourceEvents = recordByOperation.getOrDefault(true, emptyList()).stream().map(SearchConverterUtils::getNewAsMap).toList();
        repository.upsert(resourceEvents);
        var idsToDrop = recordByOperation.getOrDefault(false, emptyList()).stream().map(ResourceEvent::getId).toList();
        repository.delete(idsToDrop);
      }
    }
  }

  private boolean isInstanceEvent(ResourceEvent event) {
    var resourceName = event.getResourceName();
    return ResourceType.INSTANCE.getName().equals(resourceName)
           || ResourceType.HOLDINGS.getName().equals(resourceName)
           || ResourceType.ITEM.getName().equals(resourceName)
           || ResourceType.BOUND_WITH.getName().equals(resourceName);
  }
}
