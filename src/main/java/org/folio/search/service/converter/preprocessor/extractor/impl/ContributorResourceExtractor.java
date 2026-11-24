package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_TYPE_FIELD;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.index.ContributorKey;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.ContributorRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Log4j2
@Lazy
@Component
public class ContributorResourceExtractor extends ChildResourceExtractor {

  private final FeatureConfigService featureConfigService;

  public ContributorResourceExtractor(ContributorRepository repository, FeatureConfigService featureConfigService) {
    super(repository);
    this.featureConfigService = featureConfigService;
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
        "contributorId", entity.get("id"),
        CONTRIBUTOR_TYPE_FIELD, entity.remove(CONTRIBUTOR_TYPE_FIELD),
        "tenantId", event.getTenant(),
        "shared", shared))
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    if (entityProperties == null || !featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_CONTRIBUTORS)) {
      return Collections.emptyMap();
    }
    var typeId = entityProperties.get(CONTRIBUTOR_TYPE_FIELD);
    return ContributorKey.fromContributor(entityProperties)
      .map(key -> toEntity(key, typeId))
      .orElse(Collections.emptyMap());
  }

  private static Map<String, Object> toEntity(ContributorKey key, Object typeId) {
    var entity = new HashMap<String, Object>();
    entity.put("id", key.id());
    entity.put("name", key.name());
    entity.put("nameTypeId", key.nameTypeId());
    entity.put(AUTHORITY_ID_FIELD, key.authorityId());
    entity.put(CONTRIBUTOR_TYPE_FIELD, Objects.toString(typeId, EMPTY));
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return CONTRIBUTORS_FIELD;
  }
}
