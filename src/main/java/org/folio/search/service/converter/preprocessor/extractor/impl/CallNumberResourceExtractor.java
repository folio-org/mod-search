package org.folio.search.service.converter.preprocessor.extractor.impl;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.apache.commons.collections4.MapUtils.getMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.CollectionUtils.subtract;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.entity.CallNumberEntity;
import org.folio.search.model.entity.InstanceCallNumberEntity;
import org.folio.search.model.entity.InstanceCallNumberEntityAgg;
import org.folio.search.model.index.CallNumberResource;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.search.utils.CollectionUtils;
import org.folio.search.utils.JsonConverter;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class CallNumberResourceExtractor extends ChildResourceExtractor {

  public static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD = "effectiveCallNumberComponents";
  public static final String CALL_NUMBER_FIELD = "callNumber";
  public static final String PREFIX_FIELD = "prefix";
  public static final String SUFFIX_FIELD = "suffix";
  public static final String TYPE_ID_FIELD = "typeId";
  public static final String VOLUME_FIELD = "volume";
  public static final String CHRONOLOGY_FIELD = "chronology";
  public static final String ENUMERATION_FIELD = "enumeration";
  public static final String COPY_NUMBER_FIELD = "copyNumber";

  private final CallNumberRepository repository;
  private final JsonConverter jsonConverter;
  private final FeatureConfigService featureConfigService;

  public CallNumberResourceExtractor(CallNumberRepository repository, JsonConverter jsonConverter,
                                     FeatureConfigService featureConfigService) {
    super(repository);
    this.repository = repository;
    this.jsonConverter = jsonConverter;
    this.featureConfigService = featureConfigService;
  }

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent resource) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
      return Collections.emptyList();
    }
    var oldCallNumbers = getCallNumberResources(getOldAsMap(resource));
    var newCallNumbers = getCallNumberResources(getNewAsMap(resource));

    if (oldCallNumbers.equals(newCallNumbers)) {
      return emptyList();
    }

    var tenant = resource.getTenant();
    var callNumbersForCreate = subtract(newCallNumbers, oldCallNumbers);
    var callNumbersForDelete = subtract(oldCallNumbers, newCallNumbers);

    var idsForCreate = toIds(callNumbersForCreate);
    var idsForDelete = toIds(callNumbersForDelete);

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
  public List<ResourceEvent> prepareEventsOnSharing(ResourceEvent resource) {
    return emptyList();
  }

  @Override
  public boolean hasChildResourceChanges(ResourceEvent resource) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
      return false;
    }
    var oldCallNumber = toCallNumberEntity(getOldAsMap(resource));
    var newCallNumber = toCallNumberEntity(getNewAsMap(resource));
    return !oldCallNumber.equals(newCallNumber);
  }

  @Override
  public ResourceType resourceType() {
    return ResourceType.ITEM;
  }

  @Override
  protected List<Map<String, Object>> constructRelations(boolean shared, ResourceEvent event,
                                                         List<Map<String, Object>> entities) {
    var resourceMap = getNewAsMap(event);
    return entities.stream()
      .map(entity -> InstanceCallNumberEntity.builder()
        .callNumberId(getString(entity, "id"))
        .itemId(getString(resourceMap, "id"))
        .instanceId(getString(resourceMap, "instanceId"))
        .locationId(getString(resourceMap, "effectiveLocationId"))
        .tenantId(event.getTenant())
        .build())
      .map(jsonConverter::convertToMap)
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
      return Collections.emptyMap();
    }
    return toCallNumberEntity(entityProperties)
      .map(jsonConverter::convertToMap)
      .orElse(Collections.emptyMap());
  }

  @Override
  protected String childrenFieldName() {
    return "";
  }

  @Override
  protected Set<Map<String, Object>> getChildResources(Map<String, Object> event) {
    return Set.of(event);
  }

  private Set<CallNumberEntity> getCallNumberResources(Map<String, Object> event) {
    return toCallNumberEntity(event)
      .map(Set::of)
      .orElse(emptySet());
  }

  private List<String> toIds(Set<CallNumberEntity> subtract) {
    return subtract.stream()
      .map(CallNumberEntity::getId)
      .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<ResourceEvent> getResourceEventsForDeletion(List<String> idsForDelete,
                                                           List<InstanceCallNumberEntityAgg> entityAggList,
                                                           String tenant) {
    var notFoundEntitiesForDelete = new ArrayList<>(idsForDelete);
    var iterator = notFoundEntitiesForDelete.iterator();
    while (iterator.hasNext()) {
      var callNumber = iterator.next();
      for (var agg : entityAggList) {
        if (agg.id().equals(callNumber)) {
          iterator.remove();
        }
      }
    }

    return notFoundEntitiesForDelete.stream()
      .map(callNumberId -> toResourceDeleteEvent(callNumberId, tenant))
      .toList();
  }

  private ResourceEvent toResourceDeleteEvent(String id, String tenant) {
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(ResourceType.INSTANCE_CALL_NUMBER.getName())
      .type(ResourceEventType.DELETE);
  }

  private ResourceEvent toResourceEvent(InstanceCallNumberEntityAgg source, String tenant) {
    var id = source.id();
    var resource = new CallNumberResource(id, source.fullCallNumber(), source.callNumber(),
      source.callNumberPrefix(), source.callNumberSuffix(), source.callNumberTypeId(), source.volume(),
      source.enumeration(), source.chronology(), source.copyNumber(), source.instances());
    return new ResourceEvent()
      .id(id)
      .tenant(tenant)
      .resourceName(ResourceType.INSTANCE_CALL_NUMBER.getName())
      .type(ResourceEventType.UPDATE)
      ._new(jsonConverter.convertToMap(resource));
  }

  private Optional<CallNumberEntity> toCallNumberEntity(Map<String, Object> entityProperties) {
    var callNumberComponents = getCallNumberComponents(entityProperties);
    var callNumber = getString(callNumberComponents, CALL_NUMBER_FIELD);
    if (callNumber != null) {
      var callNumberEntity = CallNumberEntity.builder()
        .callNumber(callNumber)
        .callNumberPrefix(getString(callNumberComponents, PREFIX_FIELD))
        .callNumberSuffix(getString(callNumberComponents, SUFFIX_FIELD))
        .callNumberTypeId(getString(callNumberComponents, TYPE_ID_FIELD))
        .volume(getString(entityProperties, VOLUME_FIELD))
        .chronology(getString(entityProperties, CHRONOLOGY_FIELD))
        .enumeration(getString(entityProperties, ENUMERATION_FIELD))
        .copyNumber(getString(entityProperties, COPY_NUMBER_FIELD))
        .build();
      return Optional.of(callNumberEntity);
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getCallNumberComponents(Map<String, Object> entityProperties) {
    return (Map<String, Object>) getMap(entityProperties, EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD,
      Collections.<String, Object>emptyMap());
  }

}
