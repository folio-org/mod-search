package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.collections4.MapUtils.getMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@Lazy
public class CallNumberResourceExtractor extends ChildResourceExtractor {

  public static final String EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD = "effectiveCallNumberComponents";
  public static final String CALL_NUMBER_FIELD = "callNumber";
  public static final String PREFIX_FIELD = "prefix";
  public static final String SUFFIX_FIELD = "suffix";
  public static final String TYPE_ID_FIELD = "typeId";

  private final JsonConverter jsonConverter;
  private final FeatureConfigService featureConfigService;

  public CallNumberResourceExtractor(CallNumberRepository repository,
                                     JsonConverter jsonConverter,
                                     FeatureConfigService featureConfigService) {
    super(repository);
    this.jsonConverter = jsonConverter;
    this.featureConfigService = featureConfigService;
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
    if (entityProperties == null || !featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
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

  private Optional<CallNumberEntity> toCallNumberEntity(Map<String, Object> entityProperties) {
    var callNumberComponents = getCallNumberComponents(entityProperties);
    var callNumber = getString(callNumberComponents, CALL_NUMBER_FIELD);
    if (callNumber != null) {
      var callNumberEntity = CallNumberEntity.builder()
        .callNumber(callNumber)
        .callNumberPrefix(getString(callNumberComponents, PREFIX_FIELD))
        .callNumberSuffix(getString(callNumberComponents, SUFFIX_FIELD))
        .callNumberTypeId(getString(callNumberComponents, TYPE_ID_FIELD))
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
