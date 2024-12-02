package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_VALUE_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.SubjectRepository;
import org.folio.search.utils.ShaUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class SubjectResourceExtractor extends ChildResourceExtractor {

  public SubjectResourceExtractor(SubjectRepository repository) {
    super(repository);
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
    if (entityProperties == null) {
      return null;
    }
    var subjectValue = prepareForExpectedFormat(entityProperties.get(SUBJECT_VALUE_FIELD), 255);
    if (subjectValue.isEmpty()) {
      return null;
    }

    var authorityId = entityProperties.get(AUTHORITY_ID_FIELD);
    var sourceId = entityProperties.get(SUBJECT_SOURCE_ID_FIELD);
    var typeId = entityProperties.get(SUBJECT_TYPE_ID_FIELD);
    var id = ShaUtils.sha(subjectValue,
      Objects.toString(authorityId, EMPTY), Objects.toString(sourceId, EMPTY), Objects.toString(typeId, EMPTY));

    var entity = new HashMap<String, Object>();
    entity.put("id", id);
    entity.put(SUBJECT_VALUE_FIELD, subjectValue);
    entity.put(AUTHORITY_ID_FIELD, authorityId);
    entity.put(SUBJECT_SOURCE_ID_FIELD, sourceId);
    entity.put(SUBJECT_TYPE_ID_FIELD, typeId);
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return SUBJECTS_FIELD;
  }
}
