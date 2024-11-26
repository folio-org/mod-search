package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.collections4.MapUtils.getMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchConverterUtils.getOldAsMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.entity.CallNumberEntity;
import org.folio.search.model.entity.InstanceCallNumberEntity;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
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

  private final JsonConverter jsonConverter;
  private final FeatureConfigService featureConfigService;

  public CallNumberResourceExtractor(CallNumberRepository repository, JsonConverter jsonConverter,
                                     FeatureConfigService featureConfigService) {
    super(repository);
    this.jsonConverter = jsonConverter;
    this.featureConfigService = featureConfigService;
  }

  @Override
  public List<ResourceEvent> prepareEvents(ResourceEvent resource) {
    return List.of();
  }

  @Override
  public List<ResourceEvent> prepareEventsOnSharing(ResourceEvent resource) {
    return List.of();
  }

  @Override
  public boolean hasChildResourceChanges(ResourceEvent event) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
      return false;
    }
    var oldAsMap = getOldAsMap(event);
    var newAsMap = getNewAsMap(event);
    var oldCallNumber = constructEntity(oldAsMap);
    var newCallNumber = constructEntity(newAsMap);
    return !oldCallNumber.equals(newCallNumber);
  }

  @Override
  public ResourceType resourceType() {
    return ResourceType.ITEM;
  }

  @Override
  protected List<Map<String, Object>> constructRelations(boolean shared, ResourceEvent event,
                                                         List<Map<String, Object>> entities) {
    return entities.stream()
      .map(entity -> InstanceCallNumberEntity.builder()
        .callNumberId(getString(entity, "id"))
        .itemId(getString(getNewAsMap(event), "id"))
        .instanceId(getString(getNewAsMap(event), "instanceId"))
        .locationId(getString(getNewAsMap(event), "effectiveLocationId"))
        .tenantId(event.getTenant())
        .build())
      .map(jsonConverter::convertToMap)
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    if (!featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
      return null;
    }
    var callNumberComponents =
      (Map<String, Object>) getMap(entityProperties, EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD,
        Collections.<String, Object>emptyMap());
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
      return jsonConverter.convertToMap(callNumberEntity);
    }
    return null;
  }

  @Override
  protected String childrenFieldName() {
    return "";
  }

  @Override
  protected Set<Map<String, Object>> getChildResources(Map<String, Object> event) {
    return Set.of(event);
  }

}
