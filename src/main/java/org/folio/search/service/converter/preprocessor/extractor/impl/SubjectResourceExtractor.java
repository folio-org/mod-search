package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
public class SubjectResourceExtractor extends ChildResourceExtractor {

  private final JsonConverter jsonConverter;
  private final SubjectRepository repository;

  public SubjectResourceExtractor(SubjectRepository repository, JsonConverter jsonConverter) {
    super(repository);
    this.repository = repository;
    this.jsonConverter = jsonConverter;
  }

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent event) {
    var oldSubjects = getChildResources(getOldAsMap(event));
    var newSubjects = getChildResources(getNewAsMap(event));

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

    var entityAggList = repository.fetchByIds(idsForFetch);
    var list = getResourceEventsForDeletion(idsForDelete, entityAggList, tenant);

    var list1 = entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
    return CollectionUtils.mergeSafelyToList(list, list1);
  }

  @Override
  public List<ResourceEvent> prepareEventsOnSharing(ResourceEvent event) {
    var subjects = getChildResources(getOldAsMap(event));

    if (!subjects.equals(getChildResources(getNewAsMap(event)))) {
      log.warn("Subjects are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();

    var ids = toIds(subjects);
    var entityAggList = repository.fetchByIds(ids);

    return entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
  }

  @Override
  public boolean hasChildResourceChanges(ResourceEvent event) {
    var oldSubjects = getChildResources(getOldAsMap(event));
    var newSubjects = getChildResources(getNewAsMap(event));

    return !oldSubjects.equals(newSubjects);
  }

  @Override
  public ResourceType resourceType() {
    return ResourceType.INSTANCE;
  }

  @Override
  protected List<Map<String, Object>> constructRelations(boolean shared, ResourceEvent event,
                                                         List<Map<String, Object>> entities) {
    return entities.stream()
      .map(entity -> Map.of("instanceId", event.getId(),
        "subjectId", entity.get("id"),
        "tenantId", event.getTenant(),
        "shared", shared))
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    var subjectValue = prepareForExpectedFormat(entityProperties.get(SUBJECT_VALUE_FIELD), 255);
    if (subjectValue.isEmpty()) {
      return null;
    }

    var authorityId = entityProperties.get(AUTHORITY_ID_FIELD);
    var sourceId = entityProperties.get(SUBJECT_SOURCE_ID_FIELD);
    var typeId = entityProperties.get(SUBJECT_TYPE_ID_FIELD);
    var id = ShaUtils.sha(subjectValue,
      Objects.toString(authorityId, EMPTY), Objects.toString(sourceId, EMPTY), Objects.toString(typeId, EMPTY));

    var entity = new HashMap<String, Object>();
    entity.put("id", id);
    entity.put(SUBJECT_VALUE_FIELD, subjectValue);
    entity.put(AUTHORITY_ID_FIELD, authorityId);
    entity.put(SUBJECT_SOURCE_ID_FIELD, sourceId);
    entity.put(SUBJECT_TYPE_ID_FIELD, typeId);
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return SUBJECTS_FIELD;
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
      .map(map -> getEntityId(defaultIfBlank(MapUtils.getString(map, SUBJECT_VALUE_FIELD), ""),
        MapUtils.getString(map, AUTHORITY_ID_FIELD),
        MapUtils.getString(map, SUBJECT_SOURCE_ID_FIELD),
        MapUtils.getString(map, SUBJECT_TYPE_ID_FIELD)))
      .collect(Collectors.toCollection(ArrayList::new));
  }
}
