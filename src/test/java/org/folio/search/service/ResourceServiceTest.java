package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.UPDATE;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyToDelete;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.metadata.ResourceDescription;
import org.folio.search.model.metadata.ResourceIndexingConfiguration;
import org.folio.search.repository.IndexRepository;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.repository.ResourceRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

  private static final String CUSTOM_REPOSITORY_NAME = "testRepository";

  @InjectMocks private ResourceService indexService;
  @Mock private IndexRepository indexRepository;
  @Mock private ResourceFetchService resourceFetchService;
  @Mock private PrimaryResourceRepository primaryResourceRepository;
  @Mock private ResourceDescriptionService resourceDescriptionService;
  @Mock private Map<String, ResourceRepository> resourceRepositoryBeans;
  @Mock private MultiTenantSearchDocumentConverter searchDocumentConverter;

  @Test
  void indexResources_positive() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();

    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(mapOf(RESOURCE_NAME, List.of(searchBody)));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(primaryResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescription(RESOURCE_NAME)));

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_negative_failedResponse() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getErrorIndexOperationResponse("Failed to save bulk");

    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(mapOf(RESOURCE_NAME, List.of(searchBody)));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(primaryResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescription(RESOURCE_NAME)));

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive_customResourceRepository() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();
    var customResourceRepository = mock(ResourceRepository.class);

    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescriptionWithCustomRepository()));
    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(mapOf(RESOURCE_NAME, List.of(searchBody)));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(resourceRepositoryBeans.containsKey(CUSTOM_REPOSITORY_NAME)).thenReturn(true);
    when(resourceRepositoryBeans.get(CUSTOM_REPOSITORY_NAME)).thenReturn(customResourceRepository);
    when(customResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(primaryResourceRepository.indexResources(null)).thenReturn(getSuccessIndexOperationResponse());

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_negative() {
    var resourceEvents = List.of(resourceEvent(RESOURCE_NAME, mapOf("id", randomId())));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);
    when(primaryResourceRepository.indexResources(null)).thenReturn(getSuccessIndexOperationResponse());
    when(searchDocumentConverter.convert(emptyList())).thenReturn(emptyMap());

    var actual = indexService.indexResources(resourceEvents);
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexService.indexResources(Collections.emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResourcesById_positive() {
    var resourceEvents = List.of(resourceEvent(RESOURCE_ID, RESOURCE_NAME, CREATE, null, null));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();
    var expectedDocuments = List.of(searchDocumentBody());

    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(resourceFetchService.fetchInstancesByIds(resourceEvents)).thenReturn(List.of(resourceEvent));
    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(mapOf(RESOURCE_NAME, expectedDocuments));
    when(searchDocumentConverter.convert(null)).thenReturn(emptyMap());
    when(primaryResourceRepository.indexResources(expectedDocuments)).thenReturn(expectedResponse);

    var actual = indexService.indexResourcesById(resourceEvents);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_updateEvent() {
    var newData = mapOf("id", RESOURCE_ID, "title", "new title");
    var oldData = mapOf("id", RESOURCE_ID, "title", "old title");
    var resourceEvent = resourceEvent(RESOURCE_ID, RESOURCE_NAME, UPDATE, newData, oldData);
    var fetchedEvent = resourceEvent(RESOURCE_ID, RESOURCE_NAME, CREATE, newData, null);
    var expectedResponse = getSuccessIndexOperationResponse();
    var searchBody = searchDocumentBody(asJsonString(newData));

    when(resourceFetchService.fetchInstancesByIds(List.of(resourceEvent))).thenReturn(List.of(fetchedEvent));
    when(searchDocumentConverter.convert(List.of(fetchedEvent))).thenReturn(mapOf(RESOURCE_NAME, List.of(searchBody)));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(primaryResourceRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);
    when(resourceDescriptionService.find(RESOURCE_NAME)).thenReturn(of(resourceDescription(RESOURCE_NAME)));

    var response = indexService.indexResourcesById(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_deleteEvent() {
    var expectedDocuments = List.of(searchDocumentBodyToDelete());
    var resourceEvents = List.of(resourceEvent(RESOURCE_ID, RESOURCE_NAME, DELETE));

    when(resourceFetchService.fetchInstancesByIds(null)).thenReturn(emptyList());
    when(searchDocumentConverter.convert(emptyList())).thenReturn(emptyMap());
    when(searchDocumentConverter.convert(resourceEvents)).thenReturn(mapOf(RESOURCE_NAME, expectedDocuments));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);

    var expectedResponse = getSuccessIndexOperationResponse();
    when(primaryResourceRepository.indexResources(expectedDocuments)).thenReturn(expectedResponse);

    var actual = indexService.indexResourcesById(resourceEvents);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_negative_failedEvents() {
    var resourceEvents = List.of(resourceEvent(RESOURCE_ID, RESOURCE_NAME, CREATE, null, null));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getErrorIndexOperationResponse("Bulk failed: errors: ['test-error']");
    var expectedDocuments = List.of(searchDocumentBody());

    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(resourceFetchService.fetchInstancesByIds(resourceEvents)).thenReturn(List.of(resourceEvent));
    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(mapOf(RESOURCE_NAME, expectedDocuments));
    when(searchDocumentConverter.convert(null)).thenReturn(emptyMap());
    when(primaryResourceRepository.indexResources(expectedDocuments)).thenReturn(expectedResponse);

    var actual = indexService.indexResourcesById(resourceEvents);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_emptyList() {
    var actual = indexService.indexResourcesById(emptyList());
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResourcesById_positive_null() {
    var actual = indexService.indexResourcesById(null);
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResourcesById_negative_indexNotExist() {
    var eventIds = List.of(resourceEvent(randomId(), RESOURCE_NAME, CREATE));

    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);
    when(resourceFetchService.fetchInstancesByIds(null)).thenReturn(emptyList());
    when(searchDocumentConverter.convert(null)).thenReturn(emptyMap());
    when(searchDocumentConverter.convert(emptyList())).thenReturn(emptyMap());
    when(primaryResourceRepository.indexResources(null)).thenReturn(getSuccessIndexOperationResponse());

    var actual = indexService.indexResourcesById(eventIds);

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  private static ResourceDescription resourceDescriptionWithCustomRepository() {
    var resourceIndexingConfiguration = new ResourceIndexingConfiguration();
    resourceIndexingConfiguration.setResourceRepository(CUSTOM_REPOSITORY_NAME);

    var resourceDescription = resourceDescription(RESOURCE_NAME);
    resourceDescription.setIndexingConfiguration(resourceIndexingConfiguration);

    return resourceDescription;
  }
}
