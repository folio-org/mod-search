package org.folio.search.service;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.service.IndexService.INDEX_NOT_EXISTS_ERROR;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_ID;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyForDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.folio.search.client.InstanceStorageClient;
import org.folio.search.domain.dto.ReindexRequest;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.integration.ResourceFetchService;
import org.folio.search.model.service.ResourceIdEvent;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.utils.EnvironmentUnitTest;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexServiceTest extends EnvironmentUnitTest {

  private static final String INDEX_NOT_EXISTS_MESSAGE = String.format(INDEX_NOT_EXISTS_ERROR, List.of(INDEX_NAME));

  @Mock private IndexRepository indexRepository;
  @Mock private SearchMappingsHelper mappingsHelper;
  @Mock private SearchSettingsHelper settingsHelper;
  @Mock private MultiTenantSearchDocumentConverter searchDocumentConverter;
  @Mock private InstanceStorageClient instanceStorageClient;
  @Mock private ResourceFetchService fetchService;
  @InjectMocks private IndexService indexService;

  @Test
  void createIndex() {
    var expectedResponse = getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME));

    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT))
      .thenReturn(expectedResponse);

    var indexResponse = indexService.createIndex(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void updateMappings() {
    var expectedResponse = getSuccessIndexOperationResponse();

    when(mappingsHelper.getMappings(RESOURCE_NAME)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT)).thenReturn(expectedResponse);

    var indexResponse = indexService.updateMappings(RESOURCE_NAME, TENANT_ID);
    assertThat(indexResponse).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_positive() {
    var searchBody = searchDocumentBody();
    var eventBody = TestUtils.eventBody(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();

    when(searchDocumentConverter.convert(List.of(eventBody))).thenReturn(List.of(searchBody));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(indexRepository.indexResources(List.of(searchBody))).thenReturn(expectedResponse);

    var response = indexService.indexResources(List.of(eventBody));
    assertThat(response).isEqualTo(expectedResponse);
  }

  @Test
  void indexResources_negative() {
    var eventBodies = List.of(TestUtils.eventBody(RESOURCE_NAME, mapOf("id", randomId())));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);

    assertThatThrownBy(() -> indexService.indexResources(eventBodies))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage(INDEX_NOT_EXISTS_MESSAGE);

    verifyNoInteractions(searchDocumentConverter);
    verify(indexRepository, times(0)).indexResources(any());
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexService.indexResources(Collections.emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void createIndexIfNotExist_shouldCreateIndex_indexNotExist() {
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

    when(mappingsHelper.getMappings(INSTANCE_RESOURCE)).thenReturn(EMPTY_OBJECT);
    when(settingsHelper.getSettings(INSTANCE_RESOURCE)).thenReturn(EMPTY_OBJECT);
    when(indexRepository.indexExists(indexName)).thenReturn(true, false);
    when(indexRepository.createIndex(indexName, EMPTY_OBJECT, EMPTY_OBJECT)).thenReturn(createIndexResponse);

    indexService.reindexInventory(TENANT_ID, new ReindexRequest().recreateIndex(true));

    verify(instanceStorageClient).submitReindex();
    verify(indexRepository).dropIndex(indexName);
  }

  @Test
  void reindexInventory_positive_recreateIndexIsFalse() {
    indexService.reindexInventory(TENANT_ID, new ReindexRequest());
    verify(instanceStorageClient).submitReindex();
  }

  @Test
  void reindexInventory_positive_recreateIndexIsNull() {
    indexService.reindexInventory(TENANT_ID, null);
    verify(instanceStorageClient).submitReindex();
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
    var eventBody = TestUtils.eventBody(RESOURCE_NAME, mapOf("id", randomId()));
    var expectedResponse = getSuccessIndexOperationResponse();
    var doc = searchDocumentBody();

    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(true);
    when(fetchService.fetchInstancesByIds(eventIds)).thenReturn(List.of(eventBody));
    when(searchDocumentConverter.convertIndexEventsAsMap(List.of(eventBody))).thenReturn(mapOf(RESOURCE_ID, doc));
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
    when(searchDocumentConverter.convertIndexEventsAsMap(emptyList())).thenReturn(emptyMap());
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

    assertThatThrownBy(() -> indexService.indexResourcesById(eventIds))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage(INDEX_NOT_EXISTS_MESSAGE);

    verifyNoInteractions(searchDocumentConverter);
    verify(indexRepository, times(0)).indexResources(any());
  }
}
