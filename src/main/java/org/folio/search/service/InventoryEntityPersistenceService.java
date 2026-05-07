package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchConverterUtils.getEventPayload;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchUtils.INSTANCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.Strings;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ReindexEntityType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.ReindexContext;
import org.folio.search.service.reindex.jdbc.MergeRangeRepository;
import org.folio.search.service.reindex.jdbc.ReindexJdbcRepository;
import org.folio.search.utils.SearchConverterUtils;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class InventoryEntityPersistenceService {

  private final Map<ReindexEntityType, MergeRangeRepository> repositories;

  public InventoryEntityPersistenceService(List<MergeRangeRepository> repositories) {
    this.repositories = repositories.stream().collect(Collectors.toMap(ReindexJdbcRepository::entityType, identity()));
  }

  /**
   * Persists inventory entity events (instance, holdings, item, bound-with) to the merge range DB tables.
   * Sets non-reindex context so conflict resolution uses real-time semantics.
   *
   * @param tenant         tenant ID
   * @param resourceEvents list of resource events to persist
   */
  public void persistInventoryEntities(String tenant, List<ResourceEvent> resourceEvents) {
    var recordByResource = resourceEvents.stream().collect(Collectors.groupingBy(ResourceEvent::getResourceName));

    try {
      ReindexContext.setReindexMode(false);
      for (Map.Entry<String, List<ResourceEvent>> recordCollection : recordByResource.entrySet()) {
        var resourceType = recordCollection.getKey();
        if (ResourceType.BOUND_WITH.getName().equals(resourceType)) {
          processBoundWithEvents(tenant, recordCollection);
          continue;
        }

        var repository = repositories.get(ReindexEntityType.fromValue(resourceType));
        if (repository != null) {
          var recordByOperation = getRecordByOperation(recordCollection);
          saveEntities(tenant, recordByOperation.getOrDefault(true, emptyList()), repository);
          deleteEntities(tenant, resourceType, recordByOperation.getOrDefault(false, emptyList()), repository);

          log.debug("persistInventoryEntities::Saved {} entities for resource type {} in tenant {}",
            recordCollection.getValue().size(), resourceType, tenant);
        }
      }
    } finally {
      ReindexContext.clear();
    }
  }

  private void processBoundWithEvents(String tenant, Map.Entry<String, List<ResourceEvent>> recordCollection) {
    var repository = repositories.get(ReindexEntityType.INSTANCE);
    for (ResourceEvent resourceEvent : recordCollection.getValue()) {
      boolean bound = resourceEvent.getType() != ResourceEventType.DELETE;
      var eventPayload = getEventPayload(resourceEvent);
      var id = getString(eventPayload, INSTANCE_ID_FIELD);
      repository.updateBoundWith(tenant, id, bound);
    }
  }

  private Map<Boolean, List<ResourceEvent>> getRecordByOperation(
    Map.Entry<String, List<ResourceEvent>> recordCollection) {

    return recordCollection.getValue().stream()
      .filter(resourceEvent -> {
        if (ResourceType.INSTANCE.getName().equals(resourceEvent.getResourceName())) {
          return !Strings.CS.startsWith(getResourceSource(resourceEvent), SOURCE_CONSORTIUM_PREFIX);
        }
        return true;
      })
      .collect(Collectors.groupingBy(resourceEvent -> resourceEvent.getType() != ResourceEventType.DELETE));
  }

  private void saveEntities(String tenant, List<ResourceEvent> resourceEvents, MergeRangeRepository repository) {
    var resourceToSave = resourceEvents.stream()
      .map(SearchConverterUtils::getNewAsMap)
      .toList();
    if (!resourceToSave.isEmpty()) {
      repository.saveEntities(tenant, resourceToSave);
    }
  }

  private void deleteEntities(String tenant, String resourceType,
                              List<ResourceEvent> resourceEvents, MergeRangeRepository repository) {
    var idsToDrop = resourceEvents.stream()
      .map(ResourceEvent::getId)
      .toList();
    if (!idsToDrop.isEmpty()) {
      if (ResourceType.HOLDINGS.getName().equals(resourceType) || ResourceType.ITEM.getName().equals(resourceType)) {
        repository.deleteEntitiesForTenant(idsToDrop, tenant);
      } else {
        repository.deleteEntities(idsToDrop);
      }
    }
  }
}
