package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
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
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.entity.InstanceClassificationEntityAgg;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.ClassificationRepository;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.folio.search.utils.ShaUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ClassificationResourceExtractor extends ChildResourceExtractor {

  private final JsonConverter jsonConverter;
  private final FeatureConfigService featureConfigService;
  private final ClassificationRepository repository;

  public ClassificationResourceExtractor(ClassificationRepository repository, JsonConverter jsonConverter,
                                         FeatureConfigService featureConfigService) {
    super(repository);
    this.jsonConverter = jsonConverter;
    this.featureConfigService = featureConfigService;
    this.repository = repository;
  }

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent event) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)) {
      return emptyList();
    }

    var oldClassifications = getChildResources(getOldAsMap(event));
    var newClassifications = getChildResources(getNewAsMap(event));

    if (oldClassifications.equals(newClassifications)) {
      return emptyList();
    }
    var tenant = event.getTenant();
    var classificationsForCreate = subtract(newClassifications, oldClassifications);
    var classificationsForDelete = subtract(oldClassifications, newClassifications);

    var idsForCreate = toIds(classificationsForCreate);
    var idsForDelete = toIds(classificationsForDelete);

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
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)) {
      return emptyList();
    }

    var classifications = getChildResources(getOldAsMap(event));

    if (!classifications.equals(getChildResources(getNewAsMap(event)))) {
      log.warn("Classifications are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();

    var entitiesForDelete = toIds(classifications);
    var entityAggList = repository.fetchByIds(entitiesForDelete);

    return entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
  }

  @Override
  public boolean hasChildResourceChanges(ResourceEvent event) {
    var oldClassifications = getChildResources(getOldAsMap(event));
    var newClassifications = getChildResources(getNewAsMap(event));

    return !oldClassifications.equals(newClassifications);
  }

  @Override
  protected List<Map<String, Object>> constructRelations(boolean shared, ResourceEvent event,
                                                       List<Map<String, Object>> entities) {
    return entities.stream()
      .map(entity -> Map.of("instanceId", event.getId(),
        "classificationId", entity.get("id"),
        "tenantId", event.getTenant(),
        "shared", shared))
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    var classificationNumber = prepareForExpectedFormat(entityProperties.get(CLASSIFICATION_NUMBER_FIELD), 50);
    if (classificationNumber.isEmpty()) {
      return null;
    }

    var classificationTypeId = entityProperties.get(CLASSIFICATION_TYPE_FIELD);
    var id = ShaUtils.sha(classificationNumber, Objects.toString(classificationTypeId, EMPTY));

    var entity = new HashMap<String, Object>();
    entity.put("id", id);
    entity.put(CLASSIFICATION_NUMBER_FIELD, classificationNumber);
    entity.put(CLASSIFICATION_TYPE_FIELD, classificationTypeId);
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return CLASSIFICATIONS_FIELD;
  }

  private List<ResourceEvent> getResourceEventsForDeletion(List<String> idsForDelete,
                                                           List<InstanceClassificationEntityAgg> entityAggList,
                                                           String tenant) {
    var notFoundEntitiesForDelete = new ArrayList<>(idsForDelete);
    var iterator = notFoundEntitiesForDelete.iterator();
    while (iterator.hasNext()) {
      var classification = iterator.next();
      for (InstanceClassificationEntityAgg agg : entityAggList) {
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
      .resourceName(ResourceType.INSTANCE_CLASSIFICATION.getName())
      .type(ResourceEventType.DELETE);
  }

  private ResourceEvent toResourceEvent(InstanceClassificationEntityAgg source, String tenant) {
    var id = source.id();
    var resource = new ClassificationResource(id, source.typeId(), source.number(), source.instances());
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(ResourceType.INSTANCE_CLASSIFICATION.getName())
      .type(ResourceEventType.UPDATE)
      ._new(jsonConverter.convertToMap(resource));
  }

  private String getClassificationId(String number, String typeId) {
    return ShaUtils.sha(truncate(number.replace("\\", "\\\\"), 50), typeId);
  }

  @NotNull
  private List<String> toIds(Set<Map<String, Object>> subtract) {
    return subtract.stream()
      .map(map -> getClassificationId(defaultIfBlank(MapUtils.getString(map, CLASSIFICATION_NUMBER_FIELD), ""),
        MapUtils.getString(map, CLASSIFICATION_TYPE_FIELD)))
      .collect(Collectors.toCollection(ArrayList::new));
  }
}
