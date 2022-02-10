package org.folio.search.integration;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.client.CqlQuery.exactMatchAny;
import static org.folio.search.utils.CollectionUtils.toLinkedHashSet;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.client.InventoryViewClient;
import org.folio.search.client.InventoryViewClient.InstanceView;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.TenantScopedExecutionService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceFetchService {

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
      return Collections.emptyList();
    }

    var instanceEvents = events.stream()
      .filter(event -> INSTANCE_RESOURCE.equals(event.getResourceName()))
      .collect(groupingBy(ResourceEvent::getTenant, mapping(ResourceEvent::getId, toLinkedHashSet())));

    return instanceEvents.entrySet().stream()
      .map(this::fetchInstances)
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private List<ResourceEvent> fetchInstances(Entry<String, Set<String>> entry) {
    var tenantId = entry.getKey();
    return tenantScopedExecutionService.executeTenantScoped(tenantId, () -> {
      var instanceIds = entry.getValue();
      var instanceResultList = inventoryClient.getInstances(exactMatchAny(ID_FIELD, instanceIds), instanceIds.size());
      return instanceResultList.getResult().stream()
        .map(InstanceView::toInstance)
        .map(instanceMap -> mapToResourceEvent(tenantId, instanceMap))
        .collect(toList());
    });
  }

  private static ResourceEvent mapToResourceEvent(String tenantId, Map<String, Object> instanceMap) {
    return new ResourceEvent()
      ._new(instanceMap)
      .id(getResourceEventId(instanceMap))
      .tenant(tenantId)
      .resourceName(INSTANCE_RESOURCE)
      .type(ResourceEventType.CREATE);
  }
}
