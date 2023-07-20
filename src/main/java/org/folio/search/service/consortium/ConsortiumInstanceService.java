package org.folio.search.service.consortium;

import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.SearchConverterUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

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
  private final ConsortiaTenantExecutor consortiaTenantExecutor;
  private final ConsortiaTenantService consortiaTenantService;

  /**
   * Saves instances to database for future indexing into consortium shared index.
   *
   * @param instanceEvents list of instance events
   * @return events that are not related to consortium tenants
   */
  public List<ResourceEvent> saveInstances(List<ResourceEvent> instanceEvents) {
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

      consortiaTenantExecutor.run(() -> repository.save(instances));
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

      consortiaTenantExecutor.run(() -> repository.delete(instanceIds));
    }
    return consortiumTenantEventsMap.get(false);
  }

  public List<ResourceEvent> fetchInstances(List<String> instanceIds) {
    List<ResourceEvent> resourceEvents = new ArrayList<>();

    var instances = consortiaTenantExecutor.execute(() -> repository.fetch(instanceIds));
    var instancesById =
      instances.stream().collect(Collectors.groupingBy(instance -> instance.id().instanceId()));

    for (var entry : instancesById.entrySet()) {
      Map<String, Object> mergedInstance = new HashMap<>();
      mergedInstance.put(ID_KEY, entry.getKey());
      List<Map<String, Object>> mergedHoldings = new ArrayList<>();
      List<Map<String, Object>> mergedItems = new ArrayList<>();
      for (var instance : entry.getValue()) {
        var instanceMap = jsonConverter.fromJsonToMap(instance.instance());
        if (isCentralTenant(instance.id().tenantId())) {
          mergedInstance = instanceMap;
        }
        addListItems(mergedHoldings, instanceMap, HOLDINGS_KEY);
        addListItems(mergedItems, instanceMap, ITEMS_KEY);
      }
      var resourceEvent = toResourceEvent(mergedInstance, mergedHoldings, mergedItems);
      resourceEvents.add(resourceEvent);
      mergedInstance.clear();
      mergedHoldings.clear();
      mergedItems.clear();
    }
    return resourceEvents;
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
    mergedInstance.put(HOLDINGS_KEY, new ArrayList<>(mergedHoldings));
    mergedInstance.put(ITEMS_KEY, new ArrayList<>(mergedItems));
    return new ResourceEvent().id(mergedInstance.get(ID_KEY).toString())
      .type(ResourceEventType.UPDATE)
      .resourceName(INSTANCE_RESOURCE)
      ._new(new HashMap<>(mergedInstance))
      .tenant(getCentralTenant(mergedInstance.get(TENANT_ID_KEY).toString()));
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
    return consortiaTenantService.getCentralTenant(tenantId).isPresent();
  }

  private boolean isCentralTenant(String tenantId) {
    var centralTenant = consortiaTenantService.getCentralTenant(tenantId);
    return centralTenant.isPresent() && centralTenant.get().equals(tenantId);
  }

  private String getCentralTenant(String tenantId) {
    return consortiaTenantService.getCentralTenant(tenantId)
      .orElseThrow(() -> new UnsupportedOperationException("Central tenant must exist"));
  }
}
