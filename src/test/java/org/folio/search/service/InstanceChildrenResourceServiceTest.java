package org.folio.search.service;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.ClassificationResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.ContributorResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.SubjectResourceExtractor;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
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
  private ClassificationResourceExtractor classificationResourceExtractor;
  @Mock
  private ContributorResourceExtractor contributorResourceExtractor;
  @Mock
  private SubjectResourceExtractor subjectResourceExtractor;

  private List<ChildResourceExtractor> resourceExtractors;
  private InstanceChildrenResourceService service;

  @BeforeEach
  void setUp() {
    this.resourceExtractors =
      List.of(classificationResourceExtractor, contributorResourceExtractor, subjectResourceExtractor);
    for (var resourceExtractor : resourceExtractors) {
      lenient().when(resourceExtractor.resourceType()).thenReturn(ResourceType.INSTANCE);
    }
    service = new InstanceChildrenResourceService(resourceExtractors, consortiumTenantProvider);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void persistChildren(boolean shared) {
    var events = List.of(new ResourceEvent(), new ResourceEvent());
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(shared);

    service.persistChildren(TENANT_ID, ResourceType.INSTANCE, events);

    resourceExtractors.forEach(resourceExtractor ->
      verify(resourceExtractor).persistChildren(shared, events));
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

    resourceExtractors.forEach(resourceExtractor ->
      verify(resourceExtractor).persistChildren(shared, expectedEvents));
  }

  private ResourceEvent getResourceEvent(UUID id1, Map<String, Object> payload) {
    return new ResourceEvent().id(id1.toString()).type(ResourceEventType.REINDEX)
      .resourceName(ResourceType.INSTANCE.getName()).tenant(TENANT_ID)._new(payload);
  }
}
