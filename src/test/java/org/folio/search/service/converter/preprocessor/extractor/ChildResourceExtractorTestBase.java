package org.folio.search.service.converter.preprocessor.extractor;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;

public abstract class ChildResourceExtractorTestBase {

  void persistChildrenTest(ChildResourceExtractor extractor, InstanceChildResourceRepository repository,
                           Supplier<Map<String, Object>> eventBodySupplier) {
    var eventBody = eventBodySupplier.get();
    var events = List.of(
      resourceEvent(ResourceEventType.CREATE, eventBody),
      resourceEvent(ResourceEventType.REINDEX, eventBody),
      resourceEvent(ResourceEventType.UPDATE, noMainValuesBody()),
      resourceEvent(ResourceEventType.UPDATE, eventBodySupplier.get()),
      resourceEvent(ResourceEventType.DELETE, eventBodySupplier.get()));

    var instanceIdsForDeletion = List.of(events.get(2).getId(), events.get(3).getId(), events.get(4).getId());

    extractor.persistChildren(false, events);

    verify(repository).deleteByInstanceIds(instanceIdsForDeletion);
    verify(repository).saveAll(argThat(set -> set.resourceEntities().size() == getExpectedEntitiesSize()
      && set.relationshipEntities().size() == 3));
  }

  protected int getExpectedEntitiesSize() {
    return 2;
  }

  private Map<String, Object> noMainValuesBody() {
    return Map.of(CONTRIBUTORS_FIELD, List.of(Map.of(
        AUTHORITY_ID_FIELD, UUID.randomUUID().toString(),
        SUBJECT_SOURCE_ID_FIELD, UUID.randomUUID().toString(),
        SUBJECT_TYPE_ID_FIELD, UUID.randomUUID().toString()
      )),
      SUBJECTS_FIELD, List.of(Map.of(
        AUTHORITY_ID_FIELD, UUID.randomUUID().toString(),
        SUBJECT_SOURCE_ID_FIELD, UUID.randomUUID().toString(),
        SUBJECT_TYPE_ID_FIELD, UUID.randomUUID().toString()
      )),
      CLASSIFICATIONS_FIELD, List.of(Map.of(
        CLASSIFICATION_TYPE_FIELD, UUID.randomUUID().toString()
      )));
  }

  private ResourceEvent resourceEvent(ResourceEventType type, Map<String, Object> body) {
    return new ResourceEvent()
      .id(UUID.randomUUID().toString())
      .type(type)
      .tenant(TENANT_ID)
      ._new(body);
  }
}
