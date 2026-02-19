package org.folio.support.base;

import static org.folio.search.utils.SearchUtils.AUTHORITY_ID_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATIONS_FIELD;
import static org.folio.search.utils.SearchUtils.CLASSIFICATION_TYPE_FIELD;
import static org.folio.search.utils.SearchUtils.CONTRIBUTORS_FIELD;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;
import static org.folio.search.utils.SearchUtils.SOURCE_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECTS_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_SOURCE_ID_FIELD;
import static org.folio.search.utils.SearchUtils.SUBJECT_TYPE_ID_FIELD;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.reindex.jdbc.InstanceChildResourceRepository;

public abstract class ChildResourceExtractorTestBase {

  public void persistChildrenTest(ChildResourceExtractor extractor, InstanceChildResourceRepository repository,
                                  Supplier<Map<String, Object>> eventDataSupplier) {
    var eventData = eventDataSupplier.get();
    var resource = eventData.get("resource").toString();
    @SuppressWarnings("unchecked")
    var eventBodySupplier = (Supplier<Map<String, Object>>) () ->
      (Map<String, Object>) eventDataSupplier.get().get("body");
    var eventBody = eventBodySupplier.get();
    var oldBody = new HashMap<>(eventBodySupplier.get());
    var newBody = new HashMap<>(eventBodySupplier.get());
    oldBody.put(SOURCE_FIELD, "FOLIO");
    newBody.put(SOURCE_FIELD, SOURCE_CONSORTIUM_PREFIX + "FOLIO");
    var events = getResourceEvents(resource, eventBody, eventBodySupplier);

    var instanceIdsForDeletion = List.of(events.get(2).getId(), events.get(3).getId(), events.get(4).getId());

    extractor.persistChildren(TENANT_ID, false, events);

    verify(repository, times(1)).deleteByInstanceIds(
      argThat(list -> list.equals(instanceIdsForDeletion)),
      argThat(tenant -> tenant == null || tenant.equals(TENANT_ID)));
    verify(repository).saveAll(argThat(set -> set.resourceEntities().size() == getExpectedEntitiesSize()
                                              && set.relationshipEntities().size() == 3));
  }

  public void shouldNotPersistEmptyChildrenTest(ChildResourceExtractor extractor,
                                                InstanceChildResourceRepository repository,
                                                Supplier<Map<String, Object>> eventBodySupplier) {
    var events = List.of(resourceCreateEvent(eventBodySupplier.get()));

    extractor.persistChildren(TENANT_ID, false, events);

    verify(repository).saveAll(argThat(set -> set.resourceEntities().isEmpty()
                                              && set.relationshipEntities().isEmpty()));
  }

  protected int getExpectedEntitiesSize() {
    return 2;
  }

  private List<ResourceEvent> getResourceEvents(String resource, Map<String, Object> eventBody,
                                                Supplier<Map<String, Object>> eventBodySupplier) {
    return List.of(
      resourceEvent(ResourceEventType.CREATE, resource, eventBody),
      resourceEvent(ResourceEventType.REINDEX, resource, eventBody),
      resourceEvent(ResourceEventType.UPDATE, resource, noMainValuesBody()),
      resourceEvent(ResourceEventType.UPDATE, resource, eventBodySupplier.get()),
      resourceEvent(ResourceEventType.DELETE, resource, eventBodySupplier.get()));
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

  private ResourceEvent resourceCreateEvent(Map<String, Object> body) {
    return resourceEvent(ResourceEventType.CREATE, null, null, body);
  }

  private ResourceEvent resourceEvent(ResourceEventType type, String resource, Map<String, Object> body) {
    return resourceEvent(type, resource, null, body);
  }

  private ResourceEvent resourceEvent(ResourceEventType type,
                                      String resource,
                                      Map<String, Object> oldBody,
                                      Map<String, Object> newBody) {
    return new ResourceEvent()
      .id(UUID.randomUUID().toString())
      .type(type)
      .resourceName(resource)
      .tenant(TENANT_ID)
      ._new(newBody)
      .old(oldBody);
  }
}
