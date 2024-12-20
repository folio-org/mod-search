package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_TYPE_FIELD;
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
public class ContributorResourceExtractor extends ChildResourceExtractor {

  private final JsonConverter jsonConverter;
  private final ContributorRepository repository;

  public ContributorResourceExtractor(ContributorRepository repository, JsonConverter jsonConverter) {
    super(repository);
    this.jsonConverter = jsonConverter;
    this.repository = repository;
  }

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent event) {
    var oldEntities = getChildResources(getOldAsMap(event));
    var newEntities = getChildResources(getNewAsMap(event));

    if (oldEntities.equals(newEntities)) {
      return emptyList();
    }

    var tenant = event.getTenant();
    var entitiesForCreate = subtract(newEntities, oldEntities);
    var entitiesForDelete = subtract(oldEntities, newEntities);

    var idsForCreate = toIds(entitiesForCreate);
    var idsForDelete = toIds(entitiesForDelete);

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
    var entities = getChildResources(getOldAsMap(event));

    if (!entities.equals(getChildResources(getNewAsMap(event)))) {
      log.warn("Contributors are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();

    var ids = toIds(entities);
    var entityAggList = repository.fetchByIds(ids);

    return entityAggList.stream()
      .map(e -> toResourceEvent(e, tenant))
      .toList();
  }

  @Override
  public boolean hasChildResourceChanges(ResourceEvent event) {
    var oldContributors = getChildResources(getOldAsMap(event));
    var newContributors = getChildResources(getNewAsMap(event));

    return !oldContributors.equals(newContributors);
  }

  @Override
  protected List<Map<String, Object>> constructRelations(boolean shared, ResourceEvent event,
                                                         List<Map<String, Object>> entities) {
    return entities.stream()
      .map(entity -> Map.of("instanceId", event.getId(),
        "contributorId", entity.get("id"),
        CONTRIBUTOR_TYPE_FIELD, entity.remove(CONTRIBUTOR_TYPE_FIELD),
        "tenantId", event.getTenant(),
        "shared", shared))
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    if (entityProperties == null) {
      return null;
    }
    var contributorName = prepareForExpectedFormat(entityProperties.get("name"), 255);
    if (contributorName.isBlank()) {
      return null;
    }

    var nameTypeId = entityProperties.get("contributorNameTypeId");
    var authorityId = entityProperties.get(AUTHORITY_ID_FIELD);
    var id = ShaUtils.sha(contributorName, Objects.toString(nameTypeId, EMPTY), Objects.toString(authorityId, EMPTY));
    var typeId = entityProperties.get(CONTRIBUTOR_TYPE_FIELD);

    var entity = new HashMap<String, Object>();
    entity.put("id", id);
    entity.put("name", contributorName);
    entity.put("nameTypeId", nameTypeId);
    entity.put(AUTHORITY_ID_FIELD, authorityId);
    entity.put(CONTRIBUTOR_TYPE_FIELD, Objects.toString(typeId, EMPTY));
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return CONTRIBUTORS_FIELD;
  }

  private List<ResourceEvent> getResourceEventsForDeletion(List<String> idsForDelete,
                                                           List<InstanceContributorEntityAgg> entityAggList,
                                                           String tenant) {
    var notFoundEntitiesForDelete = new ArrayList<>(idsForDelete);
    var iterator = notFoundEntitiesForDelete.iterator();
    while (iterator.hasNext()) {
      var entityId = iterator.next();
      for (InstanceContributorEntityAgg agg : entityAggList) {
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

  private String getEntityId(String name, String typeId, String authorityId) {
    return ShaUtils.sha(truncate(name.replace("\\", "\\\\"), 255),
      typeId, authorityId);
  }

  @NotNull
  private List<String> toIds(Set<Map<String, Object>> subtract) {
    return subtract.stream()
      .map(map -> getEntityId(defaultIfBlank(MapUtils.getString(map, "name"), ""),
        MapUtils.getString(map, "contributorNameTypeId"),
        MapUtils.getString(map, AUTHORITY_ID_FIELD)))
      .collect(Collectors.toCollection(ArrayList::new));
  }
}
