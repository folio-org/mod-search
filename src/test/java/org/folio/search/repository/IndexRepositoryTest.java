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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    var documentBody = searchDocumentBody();
    var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.hasFailures()).thenReturn(false);
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = indexRepository.indexResources(singletonList(documentBody));
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
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
}
