package org.folio.search.service.converter.preprocessor;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getObject;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;
import static org.folio.search.utils.SearchConverterUtils.getResourceEventId;
import static org.folio.search.utils.SearchConverterUtils.getResourceSource;
import static org.folio.search.utils.SearchConverterUtils.isUpdateEventForResourceSharing;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.INSTANCE_CLASSIFICATION_RESOURCE;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.index.ClassificationResource;
import org.folio.search.model.index.InstanceSubResource;
import org.folio.search.repository.classification.InstanceClassificationEntity;
import org.folio.search.repository.classification.InstanceClassificationEntityAgg;
import org.folio.search.repository.classification.InstanceClassificationRepository;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.consortium.UserTenantsService;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class InstanceEventPreProcessor implements EventPreProcessor {

  private final JsonConverter jsonConverter;
  private final FeatureConfigService featureConfigService;
  private final UserTenantsService userTenantsService;
  private final InstanceClassificationRepository instanceClassificationRepository;

  @Override
  public List<ResourceEvent> preProcess(ResourceEvent event) {
    log.info("preProcess::Starting instance event pre-processing");
    if (log.isDebugEnabled()) {
      log.debug("preProcess::Starting instance event pre-processing [{}]", event);
    }

    List<ResourceEvent> events;

    if (isUpdateEventForResourceSharing(event)) {
      events = prepareClassificationEventsOnInstanceSharing(event);
    } else if (startsWith(getResourceSource(event), SOURCE_CONSORTIUM_PREFIX)) {
      log.info("preProcess::Finished instance event pre-processing. No additional events created for shadow instance.");
      return List.of(event);
    } else {
      events = prepareClassificationEvents(event);
    }

    log.info("preProcess::Finished instance event pre-processing");
    if (log.isDebugEnabled()) {
      log.debug("preProcess::Finished instance event pre-processing. Events after: [{}], ", events);
    }
    return events;
  }

  private List<ResourceEvent> prepareClassificationEventsOnInstanceSharing(ResourceEvent event) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)) {
      return emptyList();
    }

    var classifications = getClassifications(getOldAsMap(event));

    if (!classifications.equals(getClassifications(getNewAsMap(event)))) {
      log.warn("Classifications are different on Update for instance sharing");
      return emptyList();
    }

    var tenant = event.getTenant();
    var instanceId = getResourceEventId(event);
    var shared = isShared(tenant);

    var entitiesForDelete = toEntities(classifications, instanceId, tenant, shared);
    instanceClassificationRepository.deleteAll(entitiesForDelete);
    var aggregatedEntities = instanceClassificationRepository.fetchAggregatedByClassifications(entitiesForDelete);

    return getResourceEventsForUpdate(entitiesForDelete, aggregatedEntities, tenant);
  }

  private List<ResourceEvent> prepareClassificationEvents(ResourceEvent event) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CLASSIFICATIONS)) {
      return emptyList();
    }

    var oldClassifications = getClassifications(getOldAsMap(event));
    var newClassifications = getClassifications(getNewAsMap(event));

    if (oldClassifications.equals(newClassifications)) {
      return emptyList();
    }

    var tenant = event.getTenant();
    var instanceId = getResourceEventId(event);
    var shared = isShared(tenant);

    var classificationsForCreate = subtract(newClassifications, oldClassifications);
    var classificationsForDelete = subtract(oldClassifications, newClassifications);

    var entitiesForCreate = toEntities(classificationsForCreate, instanceId, tenant, shared);
    var entitiesForDelete = toEntities(classificationsForDelete, instanceId, tenant, shared);
    instanceClassificationRepository.saveAll(entitiesForCreate);
    instanceClassificationRepository.deleteAll(entitiesForDelete);

    List<InstanceClassificationEntity> entitiesForFetch = new ArrayList<>();
    entitiesForFetch.addAll(entitiesForCreate);
    entitiesForFetch.addAll(entitiesForDelete);

    var entityAggList = instanceClassificationRepository.fetchAggregatedByClassifications(entitiesForFetch);
    var list = getResourceEventsForDeletion(entitiesForDelete, entityAggList, tenant);

    var list1 = entityAggList.stream()
      .map(entities -> toResourceCreateEvent(entities, tenant))
      .toList();
    return CollectionUtils.mergeSafelyToList(list, list1);
  }

  private List<ResourceEvent> getResourceEventsForDeletion(List<InstanceClassificationEntity> entitiesForDelete,
                                                           List<InstanceClassificationEntityAgg> entityAggList,
                                                           String tenant) {
    var notFoundEntitiesForDelete = new ArrayList<>(entitiesForDelete);
    var iterator = notFoundEntitiesForDelete.iterator();
    while (iterator.hasNext()) {
      var classification = iterator.next();
      for (InstanceClassificationEntityAgg agg : entityAggList) {
        if (agg.number().equals(classification.number())
            && agg.typeId() != null
            && agg.typeId().equals(classification.typeId())) {
          iterator.remove();
        }
      }
    }

    return notFoundEntitiesForDelete.stream()
      .map(classification -> toResourceDeleteEvent(classification, tenant))
      .toList();
  }

  private List<ResourceEvent> getResourceEventsForUpdate(List<InstanceClassificationEntity> entitiesForDelete,
                                                         List<InstanceClassificationEntityAgg> aggregatedEntities,
                                                         String tenant) {
    for (var classification : entitiesForDelete) {
      for (InstanceClassificationEntityAgg agg : aggregatedEntities) {
        if (agg.number().equals(classification.number()) && Objects.equals(agg.typeId(), classification.typeId())) {
          var subResource = InstanceSubResource.builder()
            .instanceId(classification.instanceId())
            .shared(classification.shared())
            .tenantId(tenant)
            .typeId(classification.typeId())
            .build();
          agg.instances().remove(subResource);
          break;
        }
      }
    }

    return aggregatedEntities.stream()
      .map(classification ->
        getResourceEvent(tenant, classification.number(), classification.typeId(), classification.instances(), UPDATE))
      .toList();
  }

  private ResourceEvent toResourceDeleteEvent(InstanceClassificationEntity source, String tenant) {
    return getResourceEvent(tenant, source.number(), source.typeId(), null, ResourceEventType.DELETE);
  }

  private ResourceEvent toResourceCreateEvent(InstanceClassificationEntityAgg source, String tenant) {
    return getResourceEvent(tenant, source.number(), source.typeId(), source.instances(), ResourceEventType.CREATE);
  }

  private ResourceEvent getResourceEvent(String tenant, String number, String typeId,
                                         Set<InstanceSubResource> instances, ResourceEventType eventType) {
    var id = StringUtils.deleteWhitespace(number + "|" + typeId);
    var resource = new ClassificationResource(id, typeId, number, instances);
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(INSTANCE_CLASSIFICATION_RESOURCE)
      .type(eventType)
      ._new(jsonConverter.convertToMap(resource));
  }

  @NotNull
  private static List<InstanceClassificationEntity> toEntities(
    Set<Map<String, Object>> subtract, String instanceId, String tenantId, boolean shared) {
    return subtract.stream()
      .map(map -> {
        var classificationId = InstanceClassificationEntity.Id.builder()
          .number(MapUtils.getString(map, CLASSIFICATION_NUMBER_FIELD))
          .typeId(MapUtils.getString(map, CLASSIFICATION_TYPE_FIELD))
          .instanceId(instanceId)
          .tenantId(tenantId)
          .build();
        return new InstanceClassificationEntity(classificationId, shared);
      })
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

  private boolean isShared(String tenantId) {
    var centralTenant = userTenantsService.getCentralTenant(tenantId);
    return centralTenant.isPresent() && centralTenant.get().equals(tenantId);
  }
}
