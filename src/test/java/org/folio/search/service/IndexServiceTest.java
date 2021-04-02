package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import org.folio.search.client.InstanceStorageClient;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.repository.IndexRepository;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.es.SearchMappingsHelper;
import org.folio.search.service.es.SearchSettingsHelper;
import org.folio.search.utils.TestUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexServiceTest {

  private static final String EMPTY_OBJECT = "{}";
  @Mock private IndexRepository indexRepository;
  @Mock private SearchMappingsHelper mappingsHelper;
  @Mock private SearchSettingsHelper settingsHelper;
  @Mock private MultiTenantSearchDocumentConverter searchDocumentConverter;
  @Mock private InstanceStorageClient instanceStorageClient;
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
    var searchBody = searchDocumentBody();
    var eventBody = TestUtils.eventBody(RESOURCE_NAME, mapOf("id", randomId()));

    when(searchDocumentConverter.convert(List.of(eventBody))).thenReturn(List.of(searchBody));
    when(indexRepository.indexExists(INDEX_NAME)).thenReturn(false);

    assertThatThrownBy(() -> indexService.indexResources(List.of(eventBody)))
      .isInstanceOf(SearchServiceException.class)
      .hasMessage("Cancelling bulk operation [reason: "
        + "Cannot index resources for non existing indices [indices=[test-resource_test_tenant]]]");
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
  void reindexInventory_positive() {
    indexService.reindexInventory();

    verify(instanceStorageClient).submitReindex();
  }
}
