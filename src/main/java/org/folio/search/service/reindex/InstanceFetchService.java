package org.folio.search.service.reindex;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.utils.CollectionUtils.findLast;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.IndexInstanceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.UploadInstanceRepository;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceFetchService {

  private final FolioExecutionContext context;
  private final UploadInstanceRepository instanceRepository;

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

  public List<ResourceEvent> fetchInstancesByIdsNew(List<IndexInstanceEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return emptyList();
    }

    return fetchInstancesNew(events);
  }

  private List<ResourceEvent> fetchInstancesNew(List<IndexInstanceEvent> events) {
    var instanceIds = events.stream().map(IndexInstanceEvent::instanceId).collect(Collectors.toSet());
    var tenantId = context.getTenantId();
    List<ResourceEvent> list = new ArrayList<>();
    Set<String> notFoundIds = new HashSet<>(instanceIds);
    for (Map<String, Object> instanceMap : instanceRepository.fetchByIds(instanceIds)) {
      var id = getResourceEventId(instanceMap);
      ResourceEvent resourceEvent = new ResourceEvent()
        .id(id)
        .resourceName(ResourceType.INSTANCE.getName())
        ._new(instanceMap)
        .tenant(tenantId)
        .type(ResourceEventType.CREATE);
      list.add(resourceEvent);
      notFoundIds.remove(id);
    }
    notFoundIds.forEach(id -> {
      ResourceEvent resourceEvent = new ResourceEvent()
        .id(id)
        .resourceName(ResourceType.INSTANCE.getName())
        .tenant(tenantId)
        .type(ResourceEventType.DELETE);
      list.add(resourceEvent);
    });
    return list;
  }

  private List<ResourceEvent> fetchInstances(List<ResourceEvent> events) {
    var eventsById = events.stream().collect(groupingBy(ResourceEvent::getId, LinkedHashMap::new, toList()));
    var instanceIdList = List.copyOf(eventsById.keySet());
    var tenantId = context.getTenantId();
    return instanceRepository.fetchByIds(instanceIdList).stream()
      .map(instanceMap -> mapToResourceEvent(tenantId, instanceMap, eventsById))
      .toList();
  }

  private static ResourceEvent mapToResourceEvent(String tenantId, Map<String, Object> instanceMap,
                                                  Map<String, List<ResourceEvent>> eventsById) {
    var id = getResourceEventId(instanceMap);
    var resourceEvent = new ResourceEvent().id(id).resourceName(INSTANCE.getName())._new(instanceMap).tenant(tenantId);
    var lastElement = findLast(eventsById.get(id));
    if (lastElement.isEmpty()) {
      log.warn("Source event by id not found after fetching, returning fetched value [instanceId: {}]", id);
      return resourceEvent.type(ResourceEventType.CREATE);
    }

    var sourceEvent = lastElement.get();
    return resourceEvent.type(sourceEvent.getType()).old(sourceEvent.getOld());
  }
}
