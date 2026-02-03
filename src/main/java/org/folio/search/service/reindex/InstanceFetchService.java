package org.folio.search.service.reindex;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;

import java.util.ArrayList;
import java.util.HashSet;
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
   * Fetches instances from inventory-storage module using instance IDs from IndexInstanceEvent.
   *
   * @param events list of {@link IndexInstanceEvent} objects to fetch
   * @return {@link List} of {@link ResourceEvent} object with fetched data.
   */
  public List<ResourceEvent> fetchInstancesByIds(List<IndexInstanceEvent> events) {
    if (CollectionUtils.isEmpty(events)) {
      return emptyList();
    }

    var instanceIds = events.stream()
      .map(IndexInstanceEvent::instanceId)
      .collect(Collectors.toSet());
    
    return fetchInstancesFromRepository(instanceIds);
  }

  private List<ResourceEvent> fetchInstancesFromRepository(Set<String> instanceIds) {
    var tenantId = context.getTenantId();
    List<ResourceEvent> result = new ArrayList<>();
    Set<String> notFoundIds = new HashSet<>(instanceIds);
    
    for (Map<String, Object> instanceMap : instanceRepository.fetchByIds(instanceIds)) {
      var id = getResourceEventId(instanceMap);
      result.add(createResourceEvent(id, instanceMap, tenantId, ResourceEventType.CREATE));
      notFoundIds.remove(id);
    }

    notFoundIds.forEach(id -> result.add(createResourceEvent(id, null, tenantId, ResourceEventType.DELETE)));
    
    return result;
  }

  private ResourceEvent createResourceEvent(String id, Map<String, Object> data, String tenantId, 
                                           ResourceEventType type) {
    return new ResourceEvent()
      .id(id)
      .resourceName(ResourceType.INSTANCE.getName())
      ._new(data)
      .tenant(tenantId)
      .type(type);
  }
}
