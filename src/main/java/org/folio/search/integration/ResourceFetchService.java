package org.folio.search.integration;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.beginFolioExecutionContext;
import static org.folio.spring.scope.FolioExecutionScopeExecutionContextManager.endFolioExecutionContext;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.search.domain.dto.ResourceEventBody;
import org.folio.search.domain.dto.ResourceEventBody.TypeEnum;
import org.folio.search.integration.inventory.InventoryClient;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.service.AsyncFolioExecutionContext;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceFetchService {

  private final InventoryClient inventoryClient;
  private final JsonConverter jsonConverter;
  private final FolioModuleMetadata folioModuleMetadata;

  public List<ResourceEventBody> fetchInstancesByIds(List<ResourceIdEvent> events) {
    var instanceIdEvents = events.stream()
      .filter(event -> INSTANCE_RESOURCE.equals(event.getType()))
      .collect(groupingBy(ResourceIdEvent::getTenant, mapping(ResourceIdEvent::getId, toList())));

    var resourceEventBodies = new ArrayList<ResourceEventBody>();

    instanceIdEvents.forEach((tenantId, instanceIds) ->
      resourceEventBodies.addAll(fetchInstances(tenantId, instanceIds)));
    return resourceEventBodies;
  }

  private List<ResourceEventBody> fetchInstances(String tenantId, List<String> instanceIds) {
    try {
      beginFolioExecutionContext(new AsyncFolioExecutionContext(tenantId, folioModuleMetadata));
      var instanceResultList = inventoryClient.getInstances(instanceIds);
      return instanceResultList.getResult().stream()
        .map(instance -> jsonConverter.convert(instance, new TypeReference<Map<String, Object>>() {}))
        .map(instanceMap -> new ResourceEventBody()._new(instanceMap).tenant(tenantId)
          .resourceName(INSTANCE_RESOURCE).type(TypeEnum.CREATE))
        .collect(toList());
    } finally {
      endFolioExecutionContext();
    }
  }
}
