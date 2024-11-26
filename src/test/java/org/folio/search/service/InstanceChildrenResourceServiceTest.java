package org.folio.search.service;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.utils.SearchUtils.SOURCE_CONSORTIUM_PREFIX;
import static org.folio.search.utils.SearchUtils.SOURCE_FIELD;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.event.SubResourceEvent;
import org.folio.search.model.types.ResourceType;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.converter.preprocessor.extractor.ChildResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.ClassificationResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.ContributorResourceExtractor;
import org.folio.search.service.converter.preprocessor.extractor.impl.SubjectResourceExtractor;
import org.folio.spring.testing.type.UnitTest;
import org.folio.spring.tools.kafka.FolioMessageProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceChildrenResourceServiceTest {

  @Mock
  private FolioMessageProducer<SubResourceEvent> messageProducer;
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
    service = new InstanceChildrenResourceService(messageProducer, resourceExtractors, consortiumTenantProvider);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void sendChildrenEvent(int extractorIndex) {
    var event = new ResourceEvent()
      ._new(Map.of(SOURCE_FIELD, "MARC"))
      .resourceName(ResourceType.INSTANCE.getName());
    var expectedEvent = SubResourceEvent.fromResourceEvent(event);
    when(resourceExtractors.get(extractorIndex).hasChildResourceChanges(event)).thenReturn(true);

    service.sendChildrenEvent(event);

    verify(messageProducer, times(1)).sendMessages(singletonList(expectedEvent));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2})
  void sendChildrenEvent_resourceSharing(int extractorIndex) {
    var event = resourceSharingEvent();
    var expectedEvent = SubResourceEvent.fromResourceEvent(event);
    for (int i = 0; i < resourceExtractors.size(); i++) {
      if (i != extractorIndex) {
        lenient().when(resourceExtractors.get(extractorIndex).hasChildResourceChanges(event)).thenReturn(true);
      }
    }

    service.sendChildrenEvent(event);

    verify(messageProducer, times(1)).sendMessages(singletonList(expectedEvent));
  }

  @ParameterizedTest
  @ValueSource(strings = {"MARC", "CONSORTIUM_MARC"})
  void sendChildrenEvent_noEvent(String source) {
    var event = new ResourceEvent()
      .resourceName(ResourceType.INSTANCE.getName())
      ._new(Map.of(SOURCE_FIELD, source));
    resourceExtractors.forEach(resourceExtractor ->
      when(resourceExtractor.hasChildResourceChanges(event)).thenReturn(false));

    service.sendChildrenEvent(event);

    verifyNoInteractions(messageProducer);
  }

  @Test
  void sendChildrenEvent_resourceSharing_noEvent() {
    var event = resourceSharingEvent();
    resourceExtractors.forEach(resourceExtractor ->
      when(resourceExtractor.hasChildResourceChanges(event)).thenReturn(true));

    service.sendChildrenEvent(event);

    verifyNoInteractions(messageProducer);
  }

  @Test
  void extractChildren() {
    var event = new ResourceEvent()
      .resourceName(ResourceType.INSTANCE.getName());
    resourceExtractors.forEach(resourceExtractor ->
      when(resourceExtractor.prepareEvents(event)).thenReturn(List.of(new ResourceEvent(), new ResourceEvent())));

    var result = service.extractChildren(event);

    assertThat(result).hasSize(6);
  }

  @Test
  void extractChildren_resourceSharing() {
    var event = resourceSharingEvent();
    resourceExtractors.forEach(resourceExtractor ->
      when(resourceExtractor.prepareEventsOnSharing(event))
        .thenReturn(List.of(new ResourceEvent(), new ResourceEvent())));

    var result = service.extractChildren(event);

    assertThat(result).hasSize(6);
  }

  @Test
  void extractChildren_shadowInstance() {
    var event = new ResourceEvent()
      ._new(Map.of(SOURCE_FIELD, SOURCE_CONSORTIUM_PREFIX + "MARC"));

    var result = service.extractChildren(event);

    assertThat(result).isEmpty();
    resourceExtractors.forEach(resourceExtractor -> Mockito.verify(resourceExtractor).resourceType());
    resourceExtractors.forEach(Mockito::verifyNoMoreInteractions);
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

  private ResourceEvent resourceSharingEvent() {
    return new ResourceEvent()
      .type(ResourceEventType.UPDATE)
      .resourceName(ResourceType.INSTANCE.getName())
      ._new(Map.of(SOURCE_FIELD, SOURCE_CONSORTIUM_PREFIX + "MARC"))
      .old(Map.of(SOURCE_FIELD, "MARC"));
  }
}
