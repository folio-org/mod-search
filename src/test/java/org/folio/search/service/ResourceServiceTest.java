package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_ID_SECOND;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyToDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.repository.ResourceRepository;
import org.folio.search.service.consortium.ConsortiumTenantExecutor;
import org.folio.search.service.consortium.ConsortiumTenantService;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.service.reindex.InstanceFetchService;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

  private static final String CUSTOM_REPOSITORY_NAME = "org.folio.search.service.ResourceServiceTest$TestRepository#0";

  @Mock
  private IndexRepository indexRepository;
  @Mock
  private InstanceFetchService resourceFetchService;
  @Mock
  private PrimaryResourceRepository primaryResourceRepository;
  @Mock
  private ResourceDescriptionService resourceDescriptionService;
  @Mock
  private MultiTenantSearchDocumentConverter searchDocumentConverter;
  @Mock
  private TestRepository testRepository;
  @Mock
  private ConsortiumTenantService consortiumTenantService;
  @Mock
  private ConsortiumTenantExecutor consortiumTenantExecutor;
  @Mock
  private IndexNameProvider indexNameProvider;
  @Mock
  private Map<String, ResourceRepository> resourceRepositoryBeans;
  @Mock
  private InstanceChildrenResourceService instanceChildrenResourceService;
  @InjectMocks
  private ResourceService indexService;

  @BeforeEach
  void setUp() {
    lenient().when(consortiumTenantService.getCentralTenant(any())).thenReturn(Optional.empty());
    lenient().when(consortiumTenantExecutor.execute(any(), any()))
      .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call());
    lenient().when(consortiumTenantExecutor.execute(any()))
      .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    lenient().when(indexNameProvider.getIndexName(any(ResourceEvent.class)))
      .thenAnswer(invocation -> SearchUtils.getIndexName(invocation.<ResourceEvent>getArgument(0)));
  }

  @Test
  void indexResources_positive() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(INSTANCE, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();

    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(
      mapOf(INSTANCE.getName(), List.of(searchBody)));
    when(primaryResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(INSTANCE)).thenReturn(of(resourceDescription(INSTANCE)));

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_negative_failedResponse() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(INSTANCE, mapOf("id", randomId()));
    var expectedResponse = getErrorIndexOperationResponse("Failed to save bulk");

    when(searchDocumentConverter.convert(List.of(resourceEvent)))
      .thenReturn(mapOf(INSTANCE.getName(), List.of(searchBody)));
    when(primaryResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(INSTANCE)).thenReturn(of(resourceDescription(INSTANCE)));

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive_customResourceRepository() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(INSTANCE, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();
    var customResourceRepository = mock(ResourceRepository.class);

    when(resourceDescriptionService.find(INSTANCE)).thenReturn(of(resourceDescriptionWithCustomRepository()));
    when(searchDocumentConverter.convert(List.of(resourceEvent)))
      .thenReturn(mapOf(INSTANCE.getName(), List.of(searchBody)));
    when(resourceRepositoryBeans.containsKey(CUSTOM_REPOSITORY_NAME)).thenReturn(true);
    when(resourceRepositoryBeans.get(CUSTOM_REPOSITORY_NAME)).thenReturn(customResourceRepository);
    when(customResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(primaryResourceRepository.indexResources(null)).thenReturn(getSuccessIndexOperationResponse());

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexService.indexResources(Collections.emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResourcesById_positive() {
    var resourceEvents = List.of(resourceEvent(RESOURCE_ID, INSTANCE, CREATE, null, null));
    var resourceEvent = resourceEvent(INSTANCE, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();
    var expectedDocuments = List.of(searchDocumentBody());

    when(resourceFetchService.fetchInstancesByIds(any())).thenReturn(List.of(resourceEvent));
    when(searchDocumentConverter.convert(List.of(resourceEvent)))
      .thenReturn(mapOf(INSTANCE.getName(), expectedDocuments));
    when(primaryResourceRepository.indexResources(expectedDocuments)).thenReturn(expectedResponse);

    var actual = indexService.indexInstancesById(resourceEvents);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_updateEvent() {
    var newData = mapOf("id", RESOURCE_ID, "title", "new title");
    var oldData = mapOf("id", RESOURCE_ID, "title", "old title");
    var resourceEvent = resourceEvent(RESOURCE_ID, INSTANCE, UPDATE, newData, oldData);
    var fetchedEvent = resourceEvent(RESOURCE_ID, INSTANCE, CREATE, newData, null);
    var expectedResponse = getSuccessIndexOperationResponse();
    var searchBody = searchDocumentBody(asJsonString(newData));

    when(resourceFetchService.fetchInstancesByIds(List.of(resourceEvent))).thenReturn(List.of(fetchedEvent));
    when(searchDocumentConverter.convert(List.of(fetchedEvent)))
      .thenReturn(mapOf(INSTANCE.getName(), List.of(searchBody)));
    when(primaryResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(INSTANCE)).thenReturn(of(resourceDescription(INSTANCE)));

    var response = indexService.indexInstancesById(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_moveDataBetweenInstances() {
    var oldData = mapOf("instanceId", RESOURCE_ID_SECOND, "title", "old title");
    var newData = mapOf("instanceId", RESOURCE_ID, "title", "new title");
    var resourceEvent = resourceEvent(RESOURCE_ID, INSTANCE, UPDATE, newData, oldData);
    var oldEvent = resourceEvent(RESOURCE_ID_SECOND, INSTANCE, UPDATE, oldData, null);
    var newEvent = resourceEvent(RESOURCE_ID, INSTANCE, UPDATE, newData, null);
    var fetchedEvents = List.of(resourceEvent(RESOURCE_ID_SECOND, INSTANCE, CREATE, oldData, null),
      resourceEvent(RESOURCE_ID, INSTANCE, CREATE, newData, null));
    var expectedResponse = getSuccessIndexOperationResponse();
    var searchBodies = List.of(searchDocumentBody(asJsonString(oldData)), searchDocumentBody(asJsonString(newData)));

    when(resourceFetchService.fetchInstancesByIds(List.of(oldEvent, newEvent))).thenReturn(fetchedEvents);
    when(searchDocumentConverter.convert(fetchedEvents)).thenReturn(mapOf(INSTANCE.getName(), searchBodies));
    when(primaryResourceRepository.indexResources(searchBodies)).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(INSTANCE)).thenReturn(of(resourceDescription(INSTANCE)));

    var response = indexService.indexInstancesById(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_deleteEvent() {
    var expectedDocuments = List.of(searchDocumentBodyToDelete());
    var resourceEvents = List.of(resourceEvent(RESOURCE_ID, INSTANCE, DELETE));

    when(resourceFetchService.fetchInstancesByIds(emptyList())).thenReturn(emptyList());
    when(searchDocumentConverter.convert(emptyList())).thenReturn(emptyMap());
    when(searchDocumentConverter.convert(resourceEvents)).thenReturn(mapOf(INSTANCE.getName(), expectedDocuments));

    var expectedResponse = getSuccessIndexOperationResponse();
    when(primaryResourceRepository.indexResources(expectedDocuments)).thenReturn(expectedResponse);

    var actual = indexService.indexInstancesById(resourceEvents);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_negative_failedEvents() {
    var resourceEvents = List.of(resourceEvent(RESOURCE_ID, INSTANCE, CREATE, null, null));
    var resourceEvent = resourceEvent(INSTANCE, mapOf("id", randomId()));
    var expectedResponse = getErrorIndexOperationResponse("Bulk failed: errors: ['test-error']");
    var expectedDocuments = List.of(searchDocumentBody());

    when(resourceFetchService.fetchInstancesByIds(resourceEvents)).thenReturn(List.of(resourceEvent));
    when(searchDocumentConverter.convert(List.of(resourceEvent)))
      .thenReturn(mapOf(INSTANCE.getName(), expectedDocuments));
    when(primaryResourceRepository.indexResources(expectedDocuments)).thenReturn(expectedResponse);

    var actual = indexService.indexInstancesById(resourceEvents);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_emptyList() {
    var actual = indexService.indexInstancesById(emptyList());
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResourcesById_positive_null() {
    var actual = indexService.indexInstancesById(null);
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  private static ResourceDescription resourceDescriptionWithCustomRepository() {
    var resourceIndexingConfiguration = new ResourceIndexingConfiguration();
    resourceIndexingConfiguration.setResourceRepository(CUSTOM_REPOSITORY_NAME);

    var resourceDescription = resourceDescription(INSTANCE);
    resourceDescription.setIndexingConfiguration(resourceIndexingConfiguration);

    return resourceDescription;
  }

  static class TestRepository implements ResourceRepository {
    @Override
    public FolioIndexOperationResponse indexResources(List<SearchDocumentBody> esDocumentBodies) {
      return null;
    }
  }
}
