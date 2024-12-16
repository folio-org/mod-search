package org.folio.search.integration.message.interceptor;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.ArrayList;
import java.util.Collection;
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
import org.folio.search.service.InstanceChildrenResourceService;
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
  private final InstanceChildrenResourceService instanceChildrenResourceService;

  public PopulateInstanceBatchInterceptor(List<MergeRangeRepository> repositories,
                                          ConsortiumTenantExecutor executionService,
                                          SystemUserScopedExecutionService systemUserScopedExecutionService,
                                          InstanceChildrenResourceService instanceChildrenResourceService) {
    this.repositories = repositories.stream().collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity()));
    this.executionService = executionService;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
    this.instanceChildrenResourceService = instanceChildrenResourceService;
  }

  @Override
  public ConsumerRecords<String, ResourceEvent> intercept(ConsumerRecords<String, ResourceEvent> records,
                                                          Consumer<String, ResourceEvent> consumer) {
    var recordsById = StreamSupport.stream(records.spliterator(), false)
      .filter(r -> isInstanceEvent(r.value()))
      .collect(Collectors.groupingBy(ConsumerRecord::key));

    var consumerRecords = new ArrayList<ResourceEvent>();
    for (var entry : recordsById.entrySet()) {
      var list = entry.getValue();
      if (list.size() > 1) {
        list.sort(Comparator.comparingLong(ConsumerRecord::timestamp));
      }
      consumerRecords.add(list.get(0).value());
    }
    populate(consumerRecords);
    return records;
  }

  private void populate(List<ResourceEvent> records) {
    var batchByTenant = records.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));
    batchByTenant.forEach((tenant, batch) -> systemUserScopedExecutionService.executeSystemUserScoped(tenant,
      () -> executionService.execute(() -> {
        process(tenant, batch);
        return null;
      })));

  }

  private void process(String tenant, List<ResourceEvent> batch) {
    var recordByResource = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));
    for (Map.Entry<String, List<ResourceEvent>> recordCollection : recordByResource.entrySet()) {
      if (ResourceType.BOUND_WITH.getName().equals(recordCollection.getKey())) {
        var repository = repositories.get(ReindexEntityType.INSTANCE);
        for (ResourceEvent resourceEvent : recordCollection.getValue()) {
          boolean bound = resourceEvent.getType() != ResourceEventType.DELETE;
          var eventPayload = getEventPayload(resourceEvent);
          var id = getString(eventPayload, INSTANCE_ID_FIELD);
          repository.updateBoundWith(tenant, id, bound);
        }
        continue;
      }

      var repository = repositories.get(ReindexEntityType.fromValue(recordCollection.getKey()));
      if (repository != null) {
        var recordByOperation = recordCollection.getValue().stream()
          .filter(resourceEvent -> {
            if (ResourceType.INSTANCE.getName().equals(resourceEvent.getResourceName())) {
              return !startsWith(getResourceSource(resourceEvent), SOURCE_CONSORTIUM_PREFIX);
            }
            return true;
          })
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
          deleteEntities(tenant, recordCollection.getKey(), repository, idsToDrop);
        }
        if (ResourceType.INSTANCE.getName().equals(recordCollection.getKey())) {
          var noShadowCopiesInstanceEvents = recordByOperation.values().stream().flatMap(Collection::stream).toList();
          instanceChildrenResourceService.persistChildren(tenant, noShadowCopiesInstanceEvents);
        }
      }

    }
  }

  private void deleteEntities(String tenant, String resourceType, MergeRangeRepository repository, List<String> ids) {
    if (ResourceType.HOLDINGS.getName().equals(resourceType) || ResourceType.ITEM.getName().equals(resourceType)) {
      repository.deleteEntitiesForTenant(ids, tenant);
    } else {
      repository.deleteEntities(ids);
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
