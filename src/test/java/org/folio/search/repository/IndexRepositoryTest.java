package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchResponseHelper.getErrorFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessFolioCreateIndexResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestConstants.EMPTY_OBJECT;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import java.util.List;
import org.folio.search.exception.SearchOperationException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.admin.indices.refresh.RefreshResponse;
import org.opensearch.action.admin.indices.settings.put.UpdateSettingsRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.IndicesClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.PutMappingRequest;

@UnitTest
@ExtendWith(MockitoExtension.class)
class IndexRepositoryTest {

  @InjectMocks
  private IndexRepository indexRepository;
  @Mock
  private RestHighLevelClient restHighLevelClient;
  @Mock
  private IndicesClient indices;

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
    when(indices.create(any(CreateIndexRequest.class), eq(DEFAULT))).thenThrow(new IOException("err"));

    assertThatThrownBy(
      () -> indexRepository.createIndex(INDEX_NAME, EMPTY_OBJECT, EMPTY_OBJECT))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=folio_instance_test_tenant, type=createIndexApi, message: err]");
  }

  @Test
  void updateIndexSettings_positive() throws IOException {
    var response = mock(AcknowledgedResponse.class);
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(response.isAcknowledged()).thenReturn(true);
    when(indices.putSettings(any(UpdateSettingsRequest.class), eq(DEFAULT))).thenReturn(response);

    var folioResponse = indexRepository.updateIndexSettings(INDEX_NAME, EMPTY_OBJECT);
    assertThat(folioResponse).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void updateIndexSettings_negative_failResponse() throws IOException {
    var esResponse = mock(AcknowledgedResponse.class);
    when(esResponse.isAcknowledged()).thenReturn(false);
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.putSettings(any(UpdateSettingsRequest.class), eq(DEFAULT))).thenReturn(esResponse);

    var response = indexRepository.updateIndexSettings(INDEX_NAME, EMPTY_OBJECT);
    assertThat(response).isEqualTo(getErrorIndexOperationResponse("Failed to put settings"));
  }

  @Test
  void updateIndexSettings_negative_throwsException() throws IOException {
    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.putSettings(any(UpdateSettingsRequest.class), eq(DEFAULT)))
      .thenThrow(new IOException("err"));

    assertThatThrownBy(() -> indexRepository.updateIndexSettings(INDEX_NAME, EMPTY_OBJECT))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=folio_instance_test_tenant, type=putIndexSettingsApi, message: err]");
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
        + "[index=folio_instance_test_tenant, type=putMappingsApi, message: err]");
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
    when(indices.delete(deleteIndexRequestCaptor.capture(), eq(DEFAULT))).thenReturn(new AcknowledgedResponse(true));

    indexRepository.dropIndex(INDEX_NAME);

    assertThat(deleteIndexRequestCaptor.getValue().indices()).containsExactly(INDEX_NAME);
  }

  @Test
  void refreshIndex_positive() throws IOException {
    var refreshRequest = ArgumentCaptor.forClass(RefreshRequest.class);
    var refreshResponse = mock(RefreshResponse.class);

    when(restHighLevelClient.indices()).thenReturn(indices);
    when(indices.refresh(refreshRequest.capture(), eq(DEFAULT))).thenReturn(refreshResponse);

    indexRepository.refreshIndices(INDEX_NAME);

    assertThat(refreshRequest.getValue().indices()).containsExactly(INDEX_NAME);
  }
}
