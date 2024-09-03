package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;

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
@RequiredArgsConstructor
public class ClassificationResourceExtractor implements ChildResourceExtractor {

  private final JsonConverter jsonConverter;
  private final FeatureConfigService featureConfigService;
  private final ClassificationRepository classificationRepository;

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent event) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)) {
      return emptyList();
    }

    var oldClassifications = getClassifications(getOldAsMap(event));
    var newClassifications = getClassifications(getNewAsMap(event));

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

    var entityAggList = classificationRepository.fetchClassificationsByIds(idsForFetch);
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

    var classifications = getClassifications(getOldAsMap(event));

    if (!classifications.equals(getClassifications(getNewAsMap(event)))) {
      log.warn("Classifications are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();

    var entitiesForDelete = toIds(classifications);
    var entityAggList = classificationRepository.fetchClassificationsByIds(entitiesForDelete);

    return entityAggList.stream()
      .map(entities -> toResourceEvent(entities, tenant))
      .toList();
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
    return new ResourceEvent().id(id).tenant(tenant).type(ResourceEventType.DELETE);
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
    return ShaUtils.sha(StringUtils.truncate(number.replace("\\", "\\\\"), 50), typeId);
  }

  @NotNull
  private List<String> toIds(Set<Map<String, Object>> subtract) {
    return subtract.stream()
      .map(map -> getClassificationId(MapUtils.getString(map, CLASSIFICATION_NUMBER_FIELD),
        MapUtils.getString(map, CLASSIFICATION_TYPE_FIELD)))
      .collect(Collectors.toCollection(ArrayList::new));
  }

  @SuppressWarnings("unchecked")
  private Set<Map<String, Object>> getClassifications(Map<String, Object> event) {
    var object = getObject(event, CLASSIFICATIONS_FIELD, emptyList());
    if (object == null) {
      return emptySet();
    }
    return new HashSet<>((List<Map<String, Object>>) object);
  }
}
