package org.folio.search.integration;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;
import static org.folio.search.utils.SearchUtils.ID_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.integration.inventory.InventoryViewClient;
import org.folio.search.integration.inventory.InventoryViewClient.InstanceView;
import org.folio.search.model.service.ResourceIdEvent;
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
   * @param events list of {@link ResourceIdEvent} objects to fetch
   * @return {@link List} of {@link ResourceEvent} object with fetched data.
   */
  public List<ResourceEvent> fetchInstancesByIds(List<ResourceIdEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return Collections.emptyList();
    }

    var instanceIdEvents = events.stream()
      .filter(event -> INSTANCE_RESOURCE.equals(event.getType()))
      .collect(groupingBy(ResourceIdEvent::getTenant, mapping(ResourceIdEvent::getId, toList())));

    var resourceEventBodies = new ArrayList<ResourceEvent>();

    instanceIdEvents.forEach((tenantId, instanceIds) ->
      resourceEventBodies.addAll(fetchInstances(tenantId, instanceIds)));
    return resourceEventBodies;
  }

  private List<ResourceEvent> fetchInstances(String tenantId, List<String> instanceIds) {
    return tenantScopedExecutionService.executeTenantScoped(tenantId, () -> {
      var instanceResultList = inventoryClient.getInstances(exactMatchAny(ID_FIELD, instanceIds), instanceIds.size());
      return instanceResultList.getResult().stream()
        .map(InstanceView::toInstance)
        .map(instanceMap -> createResourceEvent(tenantId, instanceMap))
        .collect(toList());
    });
  }

  private static ResourceEvent createResourceEvent(String tenantId, Map<String, Object> instanceMap) {
    return new ResourceEvent()
      ._new(instanceMap)
      .id(MapUtils.getString(instanceMap, ID_FIELD))
      .tenant(tenantId)
      .resourceName(INSTANCE_RESOURCE)
      .type(ResourceEventType.CREATE);
  }
}
