package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.getResourceName;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.resourceDescription;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyForDelete;
import static org.folio.search.utils.TestUtils.secondaryResourceDescription;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.folio.search.client.ResourceReindexClient;
import org.folio.search.domain.dto.Authority;
import org.folio.search.domain.dto.ReindexJob;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.exception.RequestValidationException;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.service.metadata.ResourceDescriptionService;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

  @InjectMocks private IndexService indexService;

  @Mock private IndexRepository indexRepository;
  @Mock private SearchMappingsHelper mappingsHelper;
  @Mock private SearchSettingsHelper settingsHelper;
  @Mock private ResourceFetchService fetchService;
  @Mock private ResourceReindexClient resourceReindexClient;
  @Mock private ResourceDescriptionService resourceDescriptionService;
  @Mock private MultiTenantSearchDocumentConverter searchDocumentConverter;

  @Test
  void createIndex() {
    var expectedResponse = getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME));

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void updateMappings() {
    var expectedResponse = getSuccessIndexOperationResponse();

    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.updateMappings(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive() {
    var searchBody = searchDocumentBody();
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();

    when(searchDocumentConverter.convert(List.of(resourceEvent))).thenReturn(List.of(searchBody));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(indexRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);

    var response = indexService.indexResources(List.of(resourceEvent));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_negative() {
    var resourceEvents = List.of(resourceEvent(RESOURCE_NAME, mapOf("id", randomId())));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);
    when(indexRepository.indexResources(emptyList())).thenReturn(getSuccessIndexOperationResponse());
    when(searchDocumentConverter.convert(emptyList())).thenReturn(emptyList());

    var actual = indexService.indexResources(resourceEvents);
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexService.indexResources(Collections.emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void createIndexIfNotExist_shouldCreateIndex_indexNotExist() {
    when(resourceDescriptionService.get(RESOURCE_NAME)).thenReturn(resourceDescription(RESOURCE_NAME));
    var indexName = getElasticsearchIndexName(RESOURCE_NAME, TENANT_ID);

    indexService.createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);

    verify(indexRepository).createIndex(eq(indexName), any(), any());
  }

  @Test
  void createIndexIfNotExist_shouldNotCreateIndex_alreadyExist() {
    var indexName = getElasticsearchIndexName(RESOURCE_NAME, TENANT_ID);
    when(indexRepository.indexExists(indexName)).thenReturn(true);

    indexService.createIndexIfNotExist(RESOURCE_NAME, TENANT_ID);

    verify(indexRepository, times(0)).createIndex(eq(indexName), any(), any());
  }

  @Test
  void reindexInventory_positive_recreateIndexIsTrue() {
    var indexName = getElasticsearchIndexName(INSTANCE_RESOURCE, TENANT_ID);
    var createIndexResponse = getSuccessFolioCreateIndexResponse(List.of(indexName));
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(mappingsHelper.getMappings(INSTANCE_RESOURCE)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(INSTANCE_RESOURCE)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.indexExists(indexName)).thenReturn(true, false);
    when(indexRepository.createIndex(indexName, EMPTY_OBJECT, EMPTY_OBJECT)).thenReturn(createIndexResponse);
    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().recreateIndex(true));

    assertThat(actual).isEqualTo(expectedResponse);
    verify(indexRepository).dropIndex(indexName);
  }

  @Test
  void reindexInventory_positive_recreateIndexIsFalse() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(resourceDescriptionService.get(INSTANCE_RESOURCE)).thenReturn(resourceDescription(INSTANCE_RESOURCE));

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest());
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_resourceNameIsNull() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().resourceName(null));
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_reindexRequestIsNull() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://instance-storage/reindex");

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    var actual = indexService.reindexInventory(TENANT_ID, null);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_positive_authorityRecord() {
    var expectedResponse = new ReindexJob().id(randomId());
    var expectedUri = URI.create("http://authority-storage/reindex");
    var resourceName = getResourceName(Authority.class);

    when(resourceReindexClient.submitReindex(expectedUri)).thenReturn(expectedResponse);
    when(resourceDescriptionService.get(resourceName)).thenReturn(resourceDescription(resourceName));

    var actual = indexService.reindexInventory(TENANT_ID, new ReindexRequest().resourceName(resourceName));

    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void reindexInventory_negative_unknownResourceName() {
    var request = new ReindexRequest().resourceName("unknown");
    when(resourceDescriptionService.get("unknown")).thenReturn(null);
    assertThatThrownBy(() -> indexService.reindexInventory(TENANT_ID, request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Reindex request contains invalid resource name");
  }

  @Test
  void reindexInventory_negative_secondaryResource() {
    var resource = "instance_subjects";
    var request = new ReindexRequest().resourceName(resource);
    when(resourceDescriptionService.get(resource)).thenReturn(secondaryResourceDescription(resource));
    assertThatThrownBy(() -> indexService.reindexInventory(TENANT_ID, request))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("Reindex request contains invalid resource name");
  }

  @Test
  void shouldDropIndexWhenExists() {
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    indexService.dropIndex(RESOURCE_NAME, TENANT_ID);
    verify(indexRepository).dropIndex(INDEX_NAME);
  }

  @Test
  void shouldNotDropIndexWhenNotExist() {
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);

    indexService.dropIndex(RESOURCE_NAME, TENANT_ID);

    verify(indexRepository, times(0)).dropIndex(INDEX_NAME);
  }

  @Test
  void canIndexResourcesById() {
    var eventIds = List.of(ResourceIdEvent.of(RESOURCE_ID, RESOURCE_NAME, TENANT_ID, INDEX));
    var resourceEvent = resourceEvent(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();
    var doc = searchDocumentBody();

    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(fetchService.fetchInstancesByIds(eventIds)).thenReturn(List.of(resourceEvent));
    when(searchDocumentConverter.convertAsMap(List.of(resourceEvent))).thenReturn(mapOf(RESOURCE_ID, doc));
    when(searchDocumentConverter.convertDeleteEventsAsMap(null)).thenReturn(emptyMap());
    when(indexRepository.indexResources(List.of(doc))).thenReturn(expectedResponse);

    var actual = indexService.indexResourcesById(eventIds);
    assertThat(actual).isEqualTo(expectedResponse);
  }

  @Test
  void indexResourcesById_positive_deleteEvent() {
    var doc = searchDocumentBodyForDelete();
    var eventIds = List.of(ResourceIdEvent.of(RESOURCE_ID, RESOURCE_NAME, TENANT_ID, DELETE));

    when(fetchService.fetchInstancesByIds(null)).thenReturn(emptyList());
    when(searchDocumentConverter.convertAsMap(emptyList())).thenReturn(emptyMap());
    when(searchDocumentConverter.convertDeleteEventsAsMap(eventIds)).thenReturn(mapOf(doc.getId(), doc));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);

    var expectedResponse = getSuccessIndexOperationResponse();
    when(indexRepository.indexResources(List.of(doc))).thenReturn(expectedResponse);

    var actual = indexService.indexResourcesById(eventIds);
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
  void cannotIndexResourcesById_indexNotExist() {
    var eventIds = List.of(ResourceIdEvent.of(randomId(), RESOURCE_NAME, TENANT_ID, INDEX));

    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);
    when(fetchService.fetchInstancesByIds(null)).thenReturn(emptyList());
    when(searchDocumentConverter.convertDeleteEventsAsMap(null)).thenReturn(emptyMap());
    when(searchDocumentConverter.convertAsMap(emptyList())).thenReturn(emptyMap());
    when(indexRepository.indexResources(emptyList())).thenReturn(getSuccessIndexOperationResponse());

    var actual = indexService.indexResourcesById(eventIds);

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }
}
