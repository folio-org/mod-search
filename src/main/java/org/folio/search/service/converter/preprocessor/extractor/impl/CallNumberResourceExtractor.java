package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.collections4.MapUtils.getMap;
import static org.apache.commons.collections4.MapUtils.getString;
import static org.apache.commons.lang3.StringUtils.truncate;
import static org.folio.search.utils.SearchConverterUtils.getNewAsMap;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.search.utils.ShaUtils;
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

  private static final int CALL_NUMBER_MAX_LENGTH = 50;
  private static final int CALL_NUMBER_PREFIX_MAX_LENGTH = 20;
  private static final int CALL_NUMBER_SUFFIX_MAX_LENGTH = 25;
  private static final int CALL_NUMBER_TYPE_MAX_LENGTH = 40;

  private final FeatureConfigService featureConfigService;

  public CallNumberResourceExtractor(CallNumberRepository repository,
                                     FeatureConfigService featureConfigService) {
    super(repository);
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
      .map(entity -> Map.<String, Object>of(
        "callNumberId", getString(entity, "id"),
        "itemId", getString(resourceMap, "id"),
        "instanceId", getString(resourceMap, "instanceId"),
        "locationId", getString(resourceMap, "effectiveLocationId"),
        "tenantId", event.getTenant()))
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    if (entityProperties == null || !featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CALL_NUMBERS)) {
      return Collections.emptyMap();
    }
    var callNumberComponents = getCallNumberComponents(entityProperties);
    var callNumber = prepareForExpectedFormat(
      getString(callNumberComponents, CALL_NUMBER_FIELD), CALL_NUMBER_MAX_LENGTH);
    if (StringUtils.isBlank(callNumber)) {
      return Collections.emptyMap();
    }
    var callNumberPrefix = truncate(getString(callNumberComponents, PREFIX_FIELD), CALL_NUMBER_PREFIX_MAX_LENGTH);
    var callNumberSuffix = truncate(getString(callNumberComponents, SUFFIX_FIELD), CALL_NUMBER_SUFFIX_MAX_LENGTH);
    var callNumberTypeId = truncate(getString(callNumberComponents, TYPE_ID_FIELD), CALL_NUMBER_TYPE_MAX_LENGTH);
    var id = ShaUtils.sha(callNumber, callNumberPrefix, callNumberSuffix, callNumberTypeId);

    var entity = new HashMap<String, Object>();
    entity.put("id", id);
    entity.put("callNumber", callNumber);
    entity.put("callNumberPrefix", callNumberPrefix);
    entity.put("callNumberSuffix", callNumberSuffix);
    entity.put("callNumberTypeId", callNumberTypeId);
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return "";
  }

  @Override
  protected Set<Map<String, Object>> getChildResources(Map<String, Object> event) {
    return Set.of(event);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getCallNumberComponents(Map<String, Object> entityProperties) {
    return (Map<String, Object>) getMap(entityProperties, EFFECTIVE_CALL_NUMBER_COMPONENTS_FIELD,
      Collections.<String, Object>emptyMap());
  }
}
