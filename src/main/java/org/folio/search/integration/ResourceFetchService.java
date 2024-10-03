package org.folio.search.integration;

import static com.google.common.collect.Lists.partition;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.CollectionUtils.findLast;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.client.InventoryViewClient;
import org.folio.search.client.InventoryViewClient.InstanceView;
import org.folio.search.client.StreamingInventoryViewClient;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceFetchService {

  private static final int BATCH_SIZE = 200;

  private final InventoryViewClient inventoryClient;
  private final StreamingInventoryViewClient streamingInventoryViewClient;
  private final FolioExecutionContext context;
  private final JsonConverter jsonConverter;

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

    return fetchInstances(events);
  }

  private List<ResourceEvent> fetchInstances(List<ResourceEvent> events) {
    var eventsById = events.stream().collect(groupingBy(ResourceEvent::getId, LinkedHashMap::new, toList()));
    var instanceIdList = List.copyOf(eventsById.keySet());
    var tenantId = context.getTenantId();
    return partition(instanceIdList, BATCH_SIZE).stream()
      .map(batchIds -> streamingInventoryViewClient.getInstances(new StreamingInventoryViewClient.IdInput(batchIds)))
      .flatMap(instanceViews -> instanceViews.map(view -> jsonConverter.fromJson(view, InstanceView.class)))
        .map(InstanceView::toInstance)
        .map(instanceMap -> mapToResourceEvent(tenantId, instanceMap, eventsById))
      .toList();
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
