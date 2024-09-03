package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.entity.InstanceContributorEntityAgg;
import org.folio.search.model.index.ContributorResource;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.ContributorRepository;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.ShaUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class ContributorResourceExtractor implements ChildResourceExtractor {

  private final JsonConverter jsonConverter;
  private final ContributorRepository contributorRepository;

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

    var entityAggList = contributorRepository.fetchByIds(idsForFetch);
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
      log.warn("Classifications are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();

    var entitiesForDelete = toIds(subjects);
    var entityAggList = contributorRepository.fetchByIds(entitiesForDelete);

    return entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
  }

  private List<ResourceEvent> getResourceEventsForDeletion(List<String> idsForDelete,
                                                           List<InstanceContributorEntityAgg> entityAggList,
                                                           String tenant) {
    var notFoundEntitiesForDelete = new ArrayList<>(idsForDelete);
    var iterator = notFoundEntitiesForDelete.iterator();
    while (iterator.hasNext()) {
      var classification = iterator.next();
      for (InstanceContributorEntityAgg agg : entityAggList) {
        if (agg.id().equals(classification)) {
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
      .resourceName(ResourceType.INSTANCE_CONTRIBUTOR.getName())
      .type(ResourceEventType.DELETE);
  }

  private ResourceEvent toResourceEvent(InstanceContributorEntityAgg source, String tenant) {
    var id = source.id();
    var resource = new ContributorResource(id, source.name(), source.nameTypeId(), source.authorityId(),
      source.instances());
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(ResourceType.INSTANCE_CONTRIBUTOR.getName())
      .type(ResourceEventType.UPDATE)
      ._new(jsonConverter.convertToMap(resource));
  }

  private String getSubjectId(String number, String typeId, String authorityId) {
    return ShaUtils.sha(StringUtils.truncate(number.replace("\\", "\\\\"), 255),
      typeId, authorityId);
  }

  @NotNull
  private List<String> toIds(Set<Map<String, Object>> subtract) {
    return subtract.stream()
      .map(map -> getSubjectId(MapUtils.getString(map, "name"),
        MapUtils.getString(map, "contributorNameTypeId"),
        MapUtils.getString(map, "authorityId")))
      .collect(Collectors.toCollection(ArrayList::new));
  }

  @SuppressWarnings("unchecked")
  private Set<Map<String, Object>> getSubjects(Map<String, Object> event) {
    var object = getObject(event, CONTRIBUTORS_FIELD, emptyList());
    if (object == null) {
      return emptySet();
    }
    return new HashSet<>((List<Map<String, Object>>) object);
  }
}
