package org.folio.search.integration;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.folio.search.client.cql.CqlQuery.exactMatchAny;
import static org.folio.search.utils.JsonConverter.MAP_TYPE_REFERENCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.domain.dto.ResourceEventBody.TypeEnum;
import org.folio.search.integration.inventory.InventoryViewClient;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.TenantScopedExecutionService;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceFetchService {

  private final InventoryViewClient inventoryClient;
  private final JsonConverter jsonConverter;
  private final TenantScopedExecutionService tenantScopedExecutionService;

  public List<ResourceEventBody> fetchInstancesByIds(List<ResourceIdEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return Collections.emptyList();
    }

    var instanceIdEvents = events.stream()
      .filter(event -> INSTANCE_RESOURCE.equals(event.getType()))
      .collect(groupingBy(ResourceIdEvent::getTenant, mapping(ResourceIdEvent::getId, toList())));

    var resourceEventBodies = new ArrayList<ResourceEventBody>();

    instanceIdEvents.forEach((tenantId, instanceIds) ->
      resourceEventBodies.addAll(fetchInstances(tenantId, instanceIds)));
    return resourceEventBodies;
  }

  private List<ResourceEventBody> fetchInstances(String tenantId, List<String> instanceIds) {
    return tenantScopedExecutionService.executeTenantScoped(tenantId, () -> {
      var instanceResultList = inventoryClient.getInstances(
        exactMatchAny("id", instanceIds), instanceIds.size());

      return instanceResultList.getResult().stream()
        .map(InventoryViewClient.InstanceView::toInstance)
        .map(instance -> jsonConverter.convert(instance, MAP_TYPE_REFERENCE))
        .map(instanceMap -> new ResourceEventBody()._new(instanceMap).tenant(tenantId)
          .resourceName(INSTANCE_RESOURCE).type(TypeEnum.CREATE))
        .collect(toList());
    });
  }
}
