package org.folio.search.service.consortium;

import static org.apache.commons.collections4.IterableUtils.toList;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.folio.search.domain.dto.ConsortiumHolding;
import org.folio.search.domain.dto.ConsortiumHoldingCollection;
import org.folio.search.domain.dto.ConsortiumItem;
import org.folio.search.domain.dto.ConsortiumItemCollection;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.ConsortiumInstanceEvent;
import org.folio.search.model.service.ConsortiumSearchContext;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

/**
 * Class designed to be executed only in scope of consortium central tenant id.
 * So, it can be expected to always have central tenant id in {@link FolioExecutionContext}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ConsortiumInstanceService {

  private static final String ID_KEY = "id";
  private static final String HOLDINGS_KEY = "holdings";
  private static final String ITEMS_KEY = "items";
  private static final String TENANT_ID_KEY = "tenantId";
  private static final String HOLDINGS_TENANT_ID_KEY = "holdings.tenantId";
  private static final String ITEMS_TENANT_ID_KEY = "items.tenantId";
  private static final String SHARED_KEY = "shared";

  private final JsonConverter jsonConverter;
  private final ConsortiumInstanceRepository repository;
  private final ConsortiumTenantExecutor consortiumTenantExecutor;
  private final ConsortiumTenantService consortiumTenantService;
  private final FolioMessageProducer<ConsortiumInstanceEvent> producer;
  private final FolioExecutionContext context;

  /**
   * Saves instances to database for future indexing into consortium shared index.
   *
   * @param instanceEvents list of instance events
   * @return events that are not related to consortium tenants
   */
  public List<ResourceEvent> saveInstances(List<ResourceEvent> instanceEvents) {
    log.info("Saving consortium instances to DB [size: {}]", instanceEvents.size());
    if (CollectionUtils.isEmpty(instanceEvents)) {
      return instanceEvents;
    }
    var consortiumTenantEventsMap = groupEventsByConsortiumTenant(instanceEvents);

    var consortiumResourceEvents = consortiumTenantEventsMap.get(true);
    if (CollectionUtils.isNotEmpty(consortiumResourceEvents)) {
      var instances = consortiumResourceEvents.stream()
        .map(this::prepareInstance)
        .map(map -> new ConsortiumInstance(
          new ConsortiumInstanceId(map.get(TENANT_ID_KEY).toString(), map.get(ID_KEY).toString()),
          jsonConverter.toJson(map)))
        .toList();

      consortiumTenantExecutor.run(() -> {
        repository.save(instances);
        prepareAndSendConsortiumInstanceEvents(instances, instance -> instance.id().instanceId());
      });
    }
    return consortiumTenantEventsMap.get(false);
  }

  /**
   * Delete instances from database that should reflect into consortium shared index.
   *
   * @param instanceEvents list of instance events
   * @return events that are not related to consortium tenants
   */
  public List<ResourceEvent> deleteInstances(List<ResourceEvent> instanceEvents) {
    if (CollectionUtils.isEmpty(instanceEvents)) {
      return instanceEvents;
    }
    var consortiumTenantEventsMap = groupEventsByConsortiumTenant(instanceEvents);
    var consortiumResourceEvents = consortiumTenantEventsMap.get(true);
    if (CollectionUtils.isNotEmpty(consortiumResourceEvents)) {
      var instanceIds = consortiumTenantEventsMap.get(true).stream()
        .map(resourceEvent -> new ConsortiumInstanceId(resourceEvent.getTenant(), resourceEvent.getId()))
        .collect(Collectors.toSet());

      consortiumTenantExecutor.run(() -> {
        repository.delete(instanceIds);
        prepareAndSendConsortiumInstanceEvents(instanceIds, ConsortiumInstanceId::instanceId);
      });
    }
    return consortiumTenantEventsMap.get(false);
  }

  public List<ResourceEvent> fetchInstances(Iterable<String> instanceIds) {
    List<ResourceEvent> resourceEvents = new ArrayList<>();

    var instanceIdList = toList(instanceIds).stream().distinct().toList();
    var instances = consortiumTenantExecutor.execute(() -> repository.fetch(instanceIdList));
    var instancesById =
      instances.stream().collect(Collectors.groupingBy(instance -> instance.id().instanceId()));

    var missedIds = ListUtils.subtract(instanceIdList, new ArrayList<>(instancesById.keySet()));
    for (var missedId : missedIds) {
      resourceEvents.add(new ResourceEvent().id(missedId.toString())
        .type(ResourceEventType.DELETE)
        .resourceName(INSTANCE_RESOURCE)
        .old(Map.of(ID_KEY, missedId))
        .tenant(context.getTenantId()));
    }

    for (var entry : instancesById.entrySet()) {
      Map<String, Object> mergedInstance = new HashMap<>();
      List<Map<String, Object>> mergedHoldings = new ArrayList<>();
      List<Map<String, Object>> mergedItems = new ArrayList<>();
      if (entry.getValue().size() == 1) {
        // if only one instance returned then there is nothing to merge (local instance)
        mergedInstance = jsonConverter.fromJsonToMap(entry.getValue().get(0).instance());
      } else {
        // if more than one instance returned then holdings/items merging required
        for (var instance : entry.getValue()) {
          var instanceMap = jsonConverter.fromJsonToMap(instance.instance());
          if (isCentralTenant(instance.id().tenantId())) {
            mergedInstance = instanceMap;
          }
          addListItems(mergedHoldings, instanceMap, HOLDINGS_KEY);
          addListItems(mergedItems, instanceMap, ITEMS_KEY);
        }
      }
      var resourceEvent = toResourceEvent(mergedInstance, mergedHoldings, mergedItems);
      resourceEvents.add(resourceEvent);
      mergedInstance.clear();
      mergedHoldings.clear();
      mergedItems.clear();
    }
    return resourceEvents;
  }

  public ConsortiumHoldingCollection fetchHoldings(ConsortiumSearchContext context) {
    List<ConsortiumHolding> holdingList = repository.fetchHoldings(new ConsortiumSearchQueryBuilder(context));
    return new ConsortiumHoldingCollection().holdings(holdingList).totalRecords(holdingList.size());
  }

  public ConsortiumItemCollection fetchItems(ConsortiumSearchContext context) {
    List<ConsortiumItem> itemList = repository.fetchItems(new ConsortiumSearchQueryBuilder(context));
    return new ConsortiumItemCollection().items(itemList).totalRecords(itemList.size());
  }

  @SuppressWarnings("unchecked")
  private void addListItems(List<Map<String, Object>> mergedList, Map<String, Object> instanceMap, String key) {
    var items = instanceMap.get(key);
    if (items instanceof List<?> list) {
      mergedList.addAll((List<Map<String, Object>>) list);
    }
  }

  private ResourceEvent toResourceEvent(Map<String, Object> mergedInstance, List<Map<String, Object>> mergedHoldings,
                                        List<Map<String, Object>> mergedItems) {
    if (!mergedHoldings.isEmpty()) {
      mergedInstance.put(HOLDINGS_KEY, new ArrayList<>(mergedHoldings));
    }
    if (!mergedItems.isEmpty()) {
      mergedInstance.put(ITEMS_KEY, new ArrayList<>(mergedItems));
    }
    return new ResourceEvent().id(mergedInstance.get(ID_KEY).toString())
      .type(ResourceEventType.UPDATE)
      .resourceName(INSTANCE_RESOURCE)
      ._new(new HashMap<>(mergedInstance))
      .tenant(context.getTenantId());
  }

  private Map<String, Object> prepareInstance(ResourceEvent resourceEvent) {
    var instance = SearchConverterUtils.getNewAsMap(resourceEvent);
    var tenant = resourceEvent.getTenant();
    SearchConverterUtils.setMapValueByPath(TENANT_ID_KEY, tenant, instance);
    SearchConverterUtils.setMapValueByPath(HOLDINGS_TENANT_ID_KEY, tenant, instance);
    SearchConverterUtils.setMapValueByPath(ITEMS_TENANT_ID_KEY, tenant, instance);
    if (isCentralTenant(tenant)) {
      SearchConverterUtils.setMapValueByPath(SHARED_KEY, true, instance);
    }
    return instance;
  }

  @NotNull
  private Map<Boolean, List<ResourceEvent>> groupEventsByConsortiumTenant(List<ResourceEvent> instanceEvents) {
    return instanceEvents.stream()
      .collect(Collectors.groupingBy(resourceEvent -> isConsortiumTenant(resourceEvent.getTenant())));
  }

  private boolean isConsortiumTenant(String tenantId) {
    return consortiumTenantService.getCentralTenant(tenantId).isPresent();
  }

  private boolean isCentralTenant(String tenantId) {
    var centralTenant = consortiumTenantService.getCentralTenant(tenantId);
    return centralTenant.isPresent() && centralTenant.get().equals(tenantId);
  }

  private <T> void prepareAndSendConsortiumInstanceEvents(Collection<T> values,
                                                          Function<T, String> instanceIdFunction) {
    var consortiumInstanceEvents = values.stream()
      .map(instanceIdFunction)
      .map(ConsortiumInstanceEvent::new)
      .toList();
    producer.sendMessages(consortiumInstanceEvents);
  }
}
