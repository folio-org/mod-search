package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.folio.search.utils.SearchResponseHelper.getErrorFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyForDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexRepositoryTest {

  @InjectMocks private IndexRepository indexRepository;
  @Mock private RestHighLevelClient restHighLevelClient;
  @Mock private IndicesClient indices;

  @Test
  void createIndex_positive() throws IOException {
    var esResponse = mock(CreateIndexResponse.class);

    when(esResponse.isAcknowledged()).thenReturn(true);
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.create(any(CreateIndexRequest.class), eq(DEFAULT))).thenReturn(esResponse);

    var response = indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT);
    assertThat(response).isEqualTo(getSuccessFolioCreateIndexResponse(List.of(INDEX_NAME)));
  }

  @Test
  void createIndex_negative_indexAlreadyExists() throws IOException {
    var esResponse = mock(CreateIndexResponse.class);

    when(esResponse.isAcknowledged()).thenReturn(false);
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.create(any(CreateIndexRequest.class), eq(DEFAULT))).thenReturn(esResponse);

    var response = indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT);
    assertThat(response).isEqualTo(getErrorFolioCreateIndexResponse(List.of(INDEX_NAME)));
  }

  @Test
  void createIndex_negative_throwsException() throws IOException {
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.create(any(CreateIndexRequest.class), eq(DEFAULT)))
      .thenThrow(new IOException("err"));

    assertThatThrownBy(
      () -> indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=folio_test-resource_test_tenant, type=createIndexApi, message: err]");
  }

  @Test
  void updateMappings_positive() throws IOException {
    var response = mock(AcknowledgedResponse.class);
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(response.isAcknowledged()).thenReturn(true);
    when(indices.putMapping(any(PutMappingRequest.class), eq(DEFAULT))).thenReturn(response);

    var folioResponse = indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT);
    assertThat(folioResponse).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void updateMappings_negative_failResponse() throws IOException {
    var esResponse = mock(AcknowledgedResponse.class);
    when(esResponse.isAcknowledged()).thenReturn(false);
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.putMapping(any(PutMappingRequest.class), eq(DEFAULT))).thenReturn(esResponse);

    var response = indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT);
    assertThat(response).isEqualTo(getErrorIndexOperationResponse("Failed to put mappings"));
  }

  @Test
  void updateMappings_negative_throwsException() throws IOException {
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.putMapping(any(PutMappingRequest.class), eq(DEFAULT)))
      .thenThrow(new IOException("err"));

    assertThatThrownBy(() -> indexRepository.updateMappings(INDEX_NAME, EMPTY_OBJECT))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=folio_test-resource_test_tenant, type=putMappingsApi, message: err]");
  }

  @Test
  void indexResources_positive() throws IOException {
    var documentBodyToCreate = searchDocumentBody();
    var documentBodyToDelete = searchDocumentBodyForDelete();
    var bulkResponse = mock(BulkResponse.class);
    var bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);

    when(bulkResponse.hasFailures()).thenReturn(false);
    when(restHighLevelClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = indexRepository.indexResources(List.of(documentBodyToCreate, documentBodyToDelete));

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(2).satisfies(requests -> {
      assertThat(requests.get(0)).isInstanceOf(IndexRequest.class);
      assertThat(requests.get(1)).isInstanceOf(DeleteRequest.class);
    });
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = indexRepository.indexResources(emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_negative_bulkFail() throws IOException {
    var documentBody = searchDocumentBody();
    var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.hasFailures()).thenReturn(true);
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = indexRepository.indexResources(singletonList(documentBody));
    assertThat(response).isEqualTo(getErrorIndexOperationResponse(null));
  }

  @Test
  void indexResources_negative_throwsException() throws IOException {
    var documentBody = searchDocumentBody();
    var documentBodies = singletonList(documentBody);
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT)))
      .thenThrow(new IOException("err"));

    assertThatThrownBy(() -> indexRepository.indexResources(documentBodies))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=folio_test-resource_test_tenant, type=bulkApi, message: err]");
  }

  @Test
  void indexExists_positive() throws IOException {
    var getIndexRequestCaptor = ArgumentCaptor.forClass(GetIndexRequest.class);

    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.exists(getIndexRequestCaptor.capture(), eq(DEFAULT))).thenReturn(true);

    var actual = indexRepository.indexExists(INDEX_NAME);

    assertThat(actual).isTrue();
    assertThat(getIndexRequestCaptor.getValue().indices()).containsExactly(INDEX_NAME);
  }

  @Test
  void dropIndex_positive() throws IOException {
    var deleteIndexRequestCaptor = ArgumentCaptor.forClass(DeleteIndexRequest.class);

    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.delete(deleteIndexRequestCaptor.capture(), eq(DEFAULT))).thenReturn(AcknowledgedResponse.TRUE);

    indexRepository.dropIndex(INDEX_NAME);

    assertThat(deleteIndexRequestCaptor.getValue().indices()).containsExactly(INDEX_NAME);
  }
}
