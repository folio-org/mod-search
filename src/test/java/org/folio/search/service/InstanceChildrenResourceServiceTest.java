package org.folio.search.service;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.CallNumberResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.ClassificationResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.ContributorResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.SubjectResourceExtractor;
import org.folio.search.service.reindex.jdbc.CallNumberRepository;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceChildrenResourceServiceTest {

  @Mock
  private ConsortiumTenantProvider consortiumTenantProvider;
  @Mock
  private CallNumberResourceExtractor callNumberResourceExtractor;
  @Mock
  private ClassificationResourceExtractor classificationResourceExtractor;
  @Mock
  private ContributorResourceExtractor contributorResourceExtractor;
  @Mock
  private SubjectResourceExtractor subjectResourceExtractor;
  @Mock
  private CallNumberRepository callNumberRepository;
  private List<ChildResourceExtractor> instanceResourceExtractors;
  private InstanceChildrenResourceService service;

  @BeforeEach
  void setUp() {
    this.instanceResourceExtractors =
      List.of(classificationResourceExtractor, contributorResourceExtractor, subjectResourceExtractor);
    for (var resourceExtractor : instanceResourceExtractors) {
      lenient().when(resourceExtractor.resourceType()).thenReturn(ResourceType.INSTANCE);
    }
    lenient().when(callNumberResourceExtractor.resourceType()).thenReturn(ResourceType.ITEM);
    var resourceExtractors = new ArrayList<>(instanceResourceExtractors);
    resourceExtractors.add(callNumberResourceExtractor);
    service = new InstanceChildrenResourceService(resourceExtractors, consortiumTenantProvider, callNumberRepository);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void persistChildren(boolean shared) {
    var events = List.of(new ResourceEvent(), new ResourceEvent());
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(shared);

    service.persistChildren(TENANT_ID, ResourceType.INSTANCE, events);

    instanceResourceExtractors.forEach(resourceExtractor ->
      verify(resourceExtractor).persistChildren(TENANT_ID, shared, events));
  }

  @Test
  void persistChildren_shouldUpdateCallNumbers_whenShared_onlyForNewInstances() {
    var date = "2024-01-01T00:00:00.000+00:00";
    var anotherDate = "2024-06-01T00:00:00.000+00:00";
    var newInstanceId = UUID.randomUUID().toString();
    var newInstance = new ResourceEvent().id(newInstanceId)
      ._new(Map.of("metadata", Map.of("createdDate", date, "updatedDate", date)));
    // Call number not updated for this one since it's not new
    var existingInstance = new ResourceEvent().id(UUID.randomUUID().toString())
      ._new(Map.of("metadata", Map.of("createdDate", date, "updatedDate", anotherDate)));
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(true);

    service.persistChildren(TENANT_ID, ResourceType.INSTANCE, List.of(newInstance, existingInstance));

    verify(callNumberRepository).updateTenantIdForCentralInstances(List.of(newInstanceId), TENANT_ID);
  }

  @Test
  void persistChildren_shouldNotUpdateCallNumbers_whenNotShared() {
    var date = "2024-01-01T00:00:00.000+00:00";
    var event = new ResourceEvent().id(UUID.randomUUID().toString())
      ._new(Map.of("metadata", Map.of("createdDate", date, "updatedDate", date)));
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(false);

    service.persistChildren(TENANT_ID, ResourceType.INSTANCE, List.of(event));

    verify(callNumberRepository, never()).updateTenantIdForCentralInstances(any(), any());
  }

  @Test
  void persistChildren_shouldNotUpdateCallNumbers_forItemResourceType() {
    var date = "2024-01-01T00:00:00.000+00:00";
    var event = new ResourceEvent().id(UUID.randomUUID().toString())
      ._new(Map.of("metadata", Map.of("createdDate", date, "updatedDate", date)));
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(true);

    service.persistChildren(TENANT_ID, ResourceType.ITEM, List.of(event));

    verify(callNumberRepository, never()).updateTenantIdForCentralInstances(any(), any());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void persistChildrenOnReindex(boolean shared) {
    var id1 = UUID.randomUUID();
    var id2 = UUID.randomUUID();
    var instances = List.of(Map.<String, Object>of("id", id1), Map.<String, Object>of("id", id2));
    var expectedEvents = List.of(getResourceEvent(id1, instances.get(0)), getResourceEvent(id2, instances.get(1)));
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(shared);

    service.persistChildrenOnReindex(TENANT_ID, ResourceType.INSTANCE, instances);

    instanceResourceExtractors.forEach(resourceExtractor ->
      verify(resourceExtractor).persistChildren(TENANT_ID, shared, expectedEvents));
  }

  private ResourceEvent getResourceEvent(UUID id1, Map<String, Object> payload) {
    return new ResourceEvent().id(id1.toString()).type(ResourceEventType.REINDEX)
      .resourceName(ResourceType.INSTANCE.getName()).tenant(TENANT_ID)._new(payload);
  }
}
