package org.folio.search.service.reindex;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.utils.CollectionUtils.findLast;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchUtils.INSTANCE_ITEM_FIELD_NAME;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections.CollectionUtils;
import org.folio.search.domain.dto.Item;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.reindex.jdbc.UploadInstanceRepository;
import org.folio.search.utils.CallNumberUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.spring.FolioExecutionContext;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class InstanceFetchService {

  private final FolioExecutionContext context;
  private final UploadInstanceRepository instanceRepository;
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
    return instanceRepository.fetchByIds(instanceIdList).stream()
      .map(this::populateEffectiveShelvingOrder)
      .map(instanceMap -> mapToResourceEvent(tenantId, instanceMap, eventsById))
      .toList();
  }

  private Map<String, Object> populateEffectiveShelvingOrder(Map<String, Object> instanceMap) {
    var o = instanceMap.get(INSTANCE_ITEM_FIELD_NAME);
    if (o instanceof List<?> items) {
      for (Object item : items) {
        var converted = jsonConverter.convert(item, Item.class);
        var shelvingOrder = CallNumberUtils.calculateShelvingOrder(converted);
        ((Map) item).put("effectiveShelvingOrder", shelvingOrder);
      }
    }
    return instanceMap;
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
