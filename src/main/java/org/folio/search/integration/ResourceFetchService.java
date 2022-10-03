package org.folio.search.integration;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.utils.CollectionUtils.findLast;
import static org.folio.search.utils.CollectionUtils.partition;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.client.InventoryViewClient;
import org.folio.search.client.InventoryViewClient.InstanceView;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.service.ResultList;
import org.folio.search.service.TenantScopedExecutionService;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceFetchService {

  private static final int BATCH_SIZE = 50;
  private final InventoryViewClient inventoryClient;
  private final TenantScopedExecutionService tenantScopedExecutionService;

  /**
   * Fetches instances from inventory-storage module using CQL query.
   *
   * @param events list of {@link ResourceEvent} objects to fetch
   * @return {@link List} of {@link ResourceEvent} object with fetched data.
   */
  public List<ResourceEvent> fetchInstancesByIds(List<ResourceEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return emptyList();
    }

    var instanceEvents = events.stream()
      .filter(event -> INSTANCE_RESOURCE.equals(event.getResourceName()))
      .collect(groupingBy(ResourceEvent::getTenant));

    return instanceEvents.entrySet().stream()
      .map(this::fetchInstances)
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private List<ResourceEvent> fetchInstances(Entry<String, List<ResourceEvent>> entry) {
    var eventsById = entry.getValue().stream().collect(groupingBy(ResourceEvent::getId, LinkedHashMap::new, toList()));
    var tenantId = entry.getKey();
    return tenantScopedExecutionService.executeTenantScoped(tenantId, () -> {
      var instanceIdList = List.copyOf(eventsById.keySet());
      return partition(instanceIdList, BATCH_SIZE).stream()
        .map(batchIds -> inventoryClient.getInstances(exactMatchAny(ID_FIELD, batchIds), batchIds.size()))
        .map(ResultList::getResult)
        .flatMap(instanceViews -> instanceViews.stream()
          .map(InstanceView::toInstance)
          .map(instanceMap -> mapToResourceEvent(tenantId, instanceMap, eventsById)))
        .collect(toList());
    });
  }

  private static ResourceEvent mapToResourceEvent(String tenantId, Map<String, Object> instanceMap,
                                                  Map<String, List<ResourceEvent>> eventsById) {
    var id = getResourceEventId(instanceMap);
    var resourceEvent = new ResourceEvent().id(id).resourceName(INSTANCE_RESOURCE)._new(instanceMap).tenant(tenantId);
    var lastElement = findLast(eventsById.get(id));
    if (lastElement.isEmpty()) {
      log.warn("Source event by id not found after fetching, returning fetched value [instanceId: {}]", id);
      return resourceEvent.type(ResourceEventType.CREATE);
    }

    var sourceEvent = lastElement.get();
    return resourceEvent.type(sourceEvent.getType()).old(sourceEvent.getOld());
  }
}
