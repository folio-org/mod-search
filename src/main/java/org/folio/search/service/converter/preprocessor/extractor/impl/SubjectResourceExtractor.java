package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.TenantConfiguredFeature;
import org.folio.search.model.index.SubjectKey;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.FeatureConfigService;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.SubjectRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Log4j2
@Lazy
@Component
public class SubjectResourceExtractor extends ChildResourceExtractor {

  private final FeatureConfigService featureConfigService;

  public SubjectResourceExtractor(SubjectRepository repository, FeatureConfigService featureConfigService) {
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
        "subjectId", entity.get("id"),
        "tenantId", event.getTenant(),
        "shared", shared))
      .toList();
  }

  @Override
  protected Map<String, Object> constructEntity(Map<String, Object> entityProperties) {
    if (entityProperties == null || !featureConfigService.isEnabled(TenantConfiguredFeature.BROWSE_SUBJECTS)) {
      return Collections.emptyMap();
    }
    return SubjectKey.fromSubject(entityProperties)
      .map(SubjectResourceExtractor::toEntity)
      .orElse(Collections.emptyMap());
  }

  private static Map<String, Object> toEntity(SubjectKey key) {
    var entity = new HashMap<String, Object>();
    entity.put("id", key.id());
    entity.put(SUBJECT_VALUE_FIELD, key.value());
    entity.put(AUTHORITY_ID_FIELD, key.authorityId());
    entity.put(SUBJECT_SOURCE_ID_FIELD, key.sourceId());
    entity.put(SUBJECT_TYPE_ID_FIELD, key.typeId());
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return SUBJECTS_FIELD;
  }
}
