package org.folio.search.service.converter.preprocessor.extractor;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.entity.ChildResourceEntityBatch;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;

@RequiredArgsConstructor
@Log4j2
public abstract class ChildResourceExtractor {

  private final InstanceChildResourceRepository repository;

  public abstract ResourceType resourceType();

  public void persistChildren(String tenantId, boolean shared, List<ResourceEvent> events) {
    deleteParentsIfNeeded(tenantId, events);

    var eventsForSaving = events.stream()
      .filter(event -> event.getType() != ResourceEventType.DELETE)
      .toList();
    if (eventsForSaving.isEmpty()) {
      return;
    }

    var entities = new HashSet<Map<String, Object>>();
    var relations = new LinkedList<Map<String, Object>>();
    eventsForSaving.forEach(event -> {
      var entitiesFromEvent = extractEntities(event);
      relations.addAll(constructRelations(shared, event, entitiesFromEvent));
      entities.addAll(entitiesFromEvent);
    });
    repository.saveAll(new ChildResourceEntityBatch(new ArrayList<>(entities), relations));
  }

  protected abstract List<Map<String, Object>> constructRelations(boolean shared, ResourceEvent event,
                                                                  List<Map<String, Object>> entities);

  protected abstract Map<String, Object> constructEntity(Map<String, Object> entityProperties);

  protected abstract String childrenFieldName();

  @SuppressWarnings("unchecked")
  protected Set<Map<String, Object>> getChildResources(Map<String, Object> event) {
    var object = getObject(event, childrenFieldName(), emptyList());
    if (object == null) {
      return emptySet();
    }
    return new HashSet<>((List<Map<String, Object>>) object);
  }

  private void deleteParentsIfNeeded(String tenantId, List<ResourceEvent> events) {
    var parentIdsForDeletion = events.stream()
      .filter(event -> event.getType() != ResourceEventType.CREATE && event.getType() != ResourceEventType.REINDEX)
      .map(ResourceEvent::getId)
      .toList();
    if (!parentIdsForDeletion.isEmpty()) {
      if (!events.isEmpty() && ResourceType.ITEM.getName().equals(events.getFirst().getResourceName())) {
        repository.deleteByInstanceIds(parentIdsForDeletion, tenantId);
      } else {
        repository.deleteByInstanceIds(parentIdsForDeletion, null);
      }
    }
  }

  private List<Map<String, Object>> extractEntities(ResourceEvent event) {
    var entities = getChildResources(getNewAsMap(event));
    return entities.stream()
      .map(this::constructEntity)
      .filter(obj -> Objects.nonNull(obj) && !obj.isEmpty())
      .toList();
  }
}
