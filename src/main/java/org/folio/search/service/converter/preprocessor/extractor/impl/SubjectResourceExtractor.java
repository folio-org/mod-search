package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.entity.InstanceSubjectEntityAgg;
import org.folio.search.model.index.SubjectResource;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.SubjectRepository;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.ShaUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SubjectResourceExtractor implements ChildResourceExtractor {

  private final JsonConverter jsonConverter;
  private final SubjectRepository subjectRepository;

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent event) {
    var oldSubjects = getSubjects(getOldAsMap(event));
    var newSubjects = getSubjects(getNewAsMap(event));

    if (oldSubjects.equals(newSubjects)) {
      return emptyList();
    }

    var tenant = event.getTenant();
    var subjectsForCreate = subtract(newSubjects, oldSubjects);
    var subjectsForDelete = subtract(oldSubjects, newSubjects);

    var idsForCreate = toIds(subjectsForCreate);
    var idsForDelete = toIds(subjectsForDelete);

    List<String> idsForFetch = new ArrayList<>();
    idsForFetch.addAll(idsForCreate);
    idsForFetch.addAll(idsForDelete);

    var entityAggList = subjectRepository.fetchByIds(idsForFetch);
    var list = getResourceEventsForDeletion(idsForDelete, entityAggList, tenant);

    var list1 = entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
    return CollectionUtils.mergeSafelyToList(list, list1);
  }

  @Override
  public List<ResourceEvent> prepareEventsOnSharing(ResourceEvent event) {
    var subjects = getSubjects(getOldAsMap(event));

    if (!subjects.equals(getSubjects(getNewAsMap(event)))) {
      log.warn("Subjects are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();

    var ids = toIds(subjects);
    var entityAggList = subjectRepository.fetchByIds(ids);

    return entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
  }

  private List<ResourceEvent> getResourceEventsForDeletion(List<String> idsForDelete,
                                                           List<InstanceSubjectEntityAgg> entityAggList,
                                                           String tenant) {
    var notFoundEntitiesForDelete = new ArrayList<>(idsForDelete);
    var iterator = notFoundEntitiesForDelete.iterator();
    while (iterator.hasNext()) {
      var entityId = iterator.next();
      for (InstanceSubjectEntityAgg agg : entityAggList) {
        if (agg.id().equals(entityId)) {
          iterator.remove();
        }
      }
    }

    return notFoundEntitiesForDelete.stream()
      .map(classificationId -> toResourceDeleteEvent(classificationId, tenant))
      .toList();
  }

  private ResourceEvent toResourceDeleteEvent(String id, String tenant) {
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(ResourceType.INSTANCE_SUBJECT.getName())
      .type(ResourceEventType.DELETE);
  }

  private ResourceEvent toResourceEvent(InstanceSubjectEntityAgg source, String tenant) {
    var id = source.id();
    var resource = new SubjectResource(id, source.value(), source.authorityId(), source.sourceId(), source.typeId(),
      source.instances());
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(ResourceType.INSTANCE_SUBJECT.getName())
      .type(ResourceEventType.UPDATE)
      ._new(jsonConverter.convertToMap(resource));
  }

  private String getEntityId(String number, String authorityId, String sourceId, String typeId) {
    return ShaUtils.sha(truncate(number.replace("\\", "\\\\"), 255), authorityId, sourceId,
      typeId);
  }

  @NotNull
  private List<String> toIds(Set<Map<String, Object>> subtract) {
    return subtract.stream()
      .map(map -> getEntityId(defaultIfBlank(MapUtils.getString(map, "value"), ""),
        MapUtils.getString(map, "authorityId"),
        MapUtils.getString(map, "sourceId"),
        MapUtils.getString(map, "typeId")))
      .collect(Collectors.toCollection(ArrayList::new));
  }

  @SuppressWarnings("unchecked")
  private Set<Map<String, Object>> getSubjects(Map<String, Object> event) {
    var object = getObject(event, SUBJECTS_FIELD, emptyList());
    if (object == null) {
      return emptySet();
    }
    return new HashSet<>((List<Map<String, Object>>) object);
  }
}
