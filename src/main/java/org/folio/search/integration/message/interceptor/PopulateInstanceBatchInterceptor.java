package org.folio.search.integration.message.interceptor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.EgressExecutionContextService;
import org.folio.search.service.InventoryEntityPersistenceService;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.stereotype.Component;

@Order(Ordered.LOWEST_PRECEDENCE)
@Component
@Log4j2
public class PopulateInstanceBatchInterceptor implements BatchInterceptor<String, ResourceEvent> {

  private final InventoryEntityPersistenceService inventoryEntityPersistenceService;
  private final ConsortiumTenantExecutor executionService;
  private final EgressExecutionContextService scopedExecutionService;

  public PopulateInstanceBatchInterceptor(InventoryEntityPersistenceService inventoryEntityPersistenceService,
                                          ConsortiumTenantExecutor executionService,
                                          EgressExecutionContextService scopedExecutionService) {
    this.inventoryEntityPersistenceService = inventoryEntityPersistenceService;
    this.executionService = executionService;
    this.scopedExecutionService = scopedExecutionService;
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
      if (processAll(list)) {
        consumerRecords.addAll(list.stream().map(ConsumerRecord::value).toList());
      } else {
        consumerRecords.add(list.getLast().value());
      }
    }
    populate(consumerRecords);
    return records;
  }

  private boolean processAll(List<ConsumerRecord<String, ResourceEvent>> records) {
    if (records.size() != 2
        || Objects.equals(records.getFirst().value().getTenant(), records.getLast().value().getTenant())) {
      return false;
    }
    return isUpdateOwnershipEvents(records)
           || isInstanceSharingEvents(records);
  }

  /**
   * Needed in case 2 item events with same id come in 1 batch on update ownership case.
   * When mod-inventory-storage send CREATE event for new tenant and DELETE event for old tenant.
   * DELETE event in such case could have higher timestamp value and
   * caller method (intercept) logic would filter out the CREATE event since both events have same id.
   * This method helps identify such case.
   */
  private boolean isUpdateOwnershipEvents(List<ConsumerRecord<String, ResourceEvent>> records) {
    var eventTypes = records.stream()
      .map(consumerRecord -> consumerRecord.value().getType())
      .toList();

    return eventTypes.contains(ResourceEventType.CREATE)
           && eventTypes.contains(ResourceEventType.DELETE);
  }

  /**
   * Needed in case 2 instance events with same id come in 1 batch on instance sharing case.
   * When mod-inventory-storage send CREATE event for central tenant and UPDATE event for member tenant.
   * UPDATE event in such case could have higher timestamp value and
   * caller method (intercept) logic would filter out the CREATE event since both events have same id.
   * This method helps identify such case.
   * UPDATE event would be filtered out later in process method since source of such event would have consortium prefix.
   *
   */
  private boolean isInstanceSharingEvents(List<ConsumerRecord<String, ResourceEvent>> records) {
    var eventTypes = records.stream()
      .map(consumerRecord -> consumerRecord.value().getType())
      .toList();

    return eventTypes.contains(ResourceEventType.CREATE)
           && eventTypes.contains(ResourceEventType.UPDATE);
  }

  private void populate(List<ResourceEvent> records) {
    var batchByTenant = records.stream().collect(Collectors.groupingBy(ResourceEvent::getTenant));
    batchByTenant.forEach((tenant, batch) ->
      scopedExecutionService.execute(tenant, () -> executionService.execute(() -> {
        process(tenant, batch);
        return null;
      })));
  }

  private void process(String tenant, List<ResourceEvent> batch) {
    inventoryEntityPersistenceService.persistInventoryEntities(tenant, batch);
  }

  private boolean isInstanceEvent(ResourceEvent event) {
    var resourceName = event.getResourceName();
    return ResourceType.INSTANCE.getName().equals(resourceName)
           || ResourceType.HOLDINGS.getName().equals(resourceName)
           || ResourceType.ITEM.getName().equals(resourceName)
           || ResourceType.BOUND_WITH.getName().equals(resourceName);
  }
}
