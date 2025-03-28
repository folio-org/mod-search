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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.log4j.Log4j2;
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
import org.folio.spring.exception.SystemUserAuthorizationException;
import org.folio.spring.service.SystemUserScopedExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.stereotype.Component;

@Order(Ordered.LOWEST_PRECEDENCE)
@Component
@Log4j2
public class PopulateInstanceBatchInterceptor implements BatchInterceptor<String, ResourceEvent> {

  private final Map<ReindexEntityType, MergeRangeRepository> repositories;
  private final ConsortiumTenantExecutor executionService;
  private final SystemUserScopedExecutionService systemUserScopedExecutionService;
  private InstanceChildrenResourceService instanceChildrenResourceService;

  public PopulateInstanceBatchInterceptor(List<MergeRangeRepository> repositories,
                                          ConsortiumTenantExecutor executionService,
                                          SystemUserScopedExecutionService systemUserScopedExecutionService) {
    this.repositories = repositories.stream().collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity()));
    this.executionService = executionService;
    this.systemUserScopedExecutionService = systemUserScopedExecutionService;
    this.instanceChildrenResourceService = null;
  }

  @Autowired(required = false)
  public void setInstanceChildrenResourceService(InstanceChildrenResourceService instanceChildrenResourceService) {
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
      consumerRecords.add(list.getLast().value());
    }
    populate(consumerRecords);
    return records;
  }

  private void populate(List<ResourceEvent> records) {
    var batchByTenant = records.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));
    batchByTenant.forEach((tenant, batch) -> {
      try {
        systemUserScopedExecutionService.executeSystemUserScoped(tenant, () -> executionService.execute(() -> {
          process(tenant, batch);
          return null;
        }));
      } catch (SystemUserAuthorizationException ex) {
        log.warn("System user authorization failed. Skip processing batch for tenant {}: {}",
          tenant, ex.getMessage(), ex);
      }
    });
  }

  private void process(String tenant, List<ResourceEvent> batch) {
    var recordByResource = batch.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));
    for (Map.Entry<String, List<ResourceEvent>> recordCollection : recordByResource.entrySet()) {
      var resourceType = recordCollection.getKey();
      if (ResourceType.BOUND_WITH.getName().equals(resourceType)) {
        processBoundWithEvents(tenant, recordCollection);
        continue;
      }

      var repository = repositories.get(ReindexEntityType.fromValue(resourceType));
      if (repository != null) {
        var recordByOperation = getRecordByOperation(recordCollection);
        saveEntities(tenant, recordByOperation.getOrDefault(true, emptyList()), repository);
        deleteEntities(tenant, resourceType, recordByOperation.getOrDefault(false, emptyList()), repository);

        if (instanceChildrenResourceService != null) {
          instanceChildrenResourceService.persistChildren(tenant, ResourceType.byName(resourceType),
            recordCollection.getValue());
        }
      }
    }
  }

  private void processBoundWithEvents(String tenant, Map.Entry<String, List<ResourceEvent>> recordCollection) {
    var repository = repositories.get(ReindexEntityType.INSTANCE);
    for (ResourceEvent resourceEvent : recordCollection.getValue()) {
      boolean bound = resourceEvent.getType() != ResourceEventType.DELETE;
      var eventPayload = getEventPayload(resourceEvent);
      var id = getString(eventPayload, INSTANCE_ID_FIELD);
      repository.updateBoundWith(tenant, id, bound);
    }
  }

  private Map<Boolean, List<ResourceEvent>> getRecordByOperation(
    Map.Entry<String, List<ResourceEvent>> recordCollection) {

    return recordCollection.getValue().stream()
      .filter(resourceEvent -> {
        if (ResourceType.INSTANCE.getName().equals(resourceEvent.getResourceName())) {
          return !startsWith(getResourceSource(resourceEvent), SOURCE_CONSORTIUM_PREFIX);
        }
        return true;
      })
      .collect(Collectors.groupingBy(resourceEvent -> resourceEvent.getType() != ResourceEventType.DELETE));
  }

  private void saveEntities(String tenant, List<ResourceEvent> resourceEvents, MergeRangeRepository repository) {
    var resourceToSave = resourceEvents.stream()
      .map(SearchConverterUtils::getNewAsMap)
      .toList();
    if (!resourceToSave.isEmpty()) {
      repository.saveEntities(tenant, resourceToSave);
    }
  }

  private void deleteEntities(String tenant, String resourceType,
                              List<ResourceEvent> resourceEvents, MergeRangeRepository repository) {
    var idsToDrop = resourceEvents.stream()
      .map(ResourceEvent::getId)
      .toList();
    if (!idsToDrop.isEmpty()) {
      if (ResourceType.HOLDINGS.getName().equals(resourceType) || ResourceType.ITEM.getName().equals(resourceType)) {
        repository.deleteEntitiesForTenant(idsToDrop, tenant);
      } else {
        repository.deleteEntities(idsToDrop);
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
