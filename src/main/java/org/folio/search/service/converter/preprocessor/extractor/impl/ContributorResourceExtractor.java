package org.folio.search.service.converter.preprocessor.extractor.impl;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTOR_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.prepareForExpectedFormat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.log4j.Log4j2;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.ContributorRepository;
import org.folio.search.utils.ShaUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class ContributorResourceExtractor extends ChildResourceExtractor {

  public ContributorResourceExtractor(ContributorRepository repository) {
    super(repository);
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
    if (entityProperties == null) {
      return null;
    }
    var contributorName = prepareForExpectedFormat(entityProperties.get("name"), 255);
    if (contributorName.isBlank()) {
      return null;
    }

    var nameTypeId = entityProperties.get("contributorNameTypeId");
    var authorityId = entityProperties.get(AUTHORITY_ID_FIELD);
    var id = ShaUtils.sha(contributorName, Objects.toString(nameTypeId, EMPTY), Objects.toString(authorityId, EMPTY));
    var typeId = entityProperties.get(CONTRIBUTOR_TYPE_FIELD);

    var entity = new HashMap<String, Object>();
    entity.put("id", id);
    entity.put("name", contributorName);
    entity.put("nameTypeId", nameTypeId);
    entity.put(AUTHORITY_ID_FIELD, authorityId);
    entity.put(CONTRIBUTOR_TYPE_FIELD, Objects.toString(typeId, EMPTY));
    return entity;
  }

  @Override
  protected String childrenFieldName() {
    return CONTRIBUTORS_FIELD;
  }
}
