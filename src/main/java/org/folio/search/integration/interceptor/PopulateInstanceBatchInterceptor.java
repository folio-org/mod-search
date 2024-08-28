package org.folio.search.integration.interceptor;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;

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
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.stereotype.Component;

@Order(Ordered.LOWEST_PRECEDENCE)
@Component
public class PopulateInstanceBatchInterceptor implements BatchInterceptor<String, ResourceEvent> {

  private final Map<ReindexEntityType, MergeRangeRepository> repositories;
  private final ConsortiumTenantExecutor executionService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;

  public PopulateInstanceBatchInterceptor(List<MergeRangeRepository> repositories,
                                          ConsortiumTenantExecutor executionService,
                                          SystemUserScopedExecutionService systemUserScopedExecutionService) {
    this.repositories = repositories.stream().collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity()));
    this.executionService = executionService;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
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
      }
      consumerRecords.add(list.get(0).value());
    }
    var count = records.count();
    System.out.println(count);
    System.out.println(consumerRecords.size());
    populate(consumerRecords);
    return records;
  }

  private void populate(List<ResourceEvent> records) {
    var batchByTenant = records.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));
    batchByTenant.forEach((tenant, batch) -> {
      systemUserScopedExecutionService.executeSystemUserScoped(tenant,
        () -> executionService.execute(() -> {
          process(tenant, batch);
          return null;
        }));
    });

  }

  private void process(String tenant, List<ResourceEvent> batch) {
    var recordByResource = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));
    for (Map.Entry<String, List<ResourceEvent>> recordCollection : recordByResource.entrySet()) {
      var repository = repositories.get(ReindexEntityType.fromValue(recordCollection.getKey()));
      if (repository != null) {
        var recordByOperation = recordCollection.getValue().stream()
          .collect(Collectors.groupingBy(resourceEvent -> resourceEvent.getType() != ResourceEventType.DELETE));
        var resourceToSave = recordByOperation.getOrDefault(true, emptyList()).stream()
          .map(SearchConverterUtils::getNewAsMap)
          .toList();
        if (!resourceToSave.isEmpty()) {
          repository.saveEntities(tenant, resourceToSave);
        }
        var idsToDrop = recordByOperation.getOrDefault(false, emptyList()).stream()
          .map(ResourceEvent::getId)
          .toList();
        if (!idsToDrop.isEmpty()) {
          repository.deleteEntities(idsToDrop);
        }
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
