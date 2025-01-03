package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_NUMBER_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.ClassificationRepository;
import org.folio.search.utils.ShaUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ClassificationResourceExtractor extends ChildResourceExtractor {

  public ClassificationResourceExtractor(ClassificationRepository repository) {
    super(repository);
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
    if (entityProperties == null) {
      return null;
    }
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
}
