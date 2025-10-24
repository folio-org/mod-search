package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.support.TestConstants.INDEX_NAME;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.searchDocumentBody;
import static org.folio.support.utils.TestUtils.searchDocumentBodyToDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import java.util.List;
import org.folio.search.configuration.properties.IndexManagementConfigurationProperties;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.ResourceType;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PrimaryResourceRepositoryTest {

  @InjectMocks
  private PrimaryResourceRepository resourceRepository;
  @Mock
  private RestHighLevelClient restHighLevelClient;
  @Mock
  private IndexNameProvider indexNameProvider;
  @Mock
  private IndexManagementConfigurationProperties indexManagementConfig;

  @BeforeEach
  void setUp() {
    lenient().when(indexNameProvider.getIndexName(any(SearchDocumentBody.class))).thenReturn("index_name");
  }

  @Test
  void indexResources_positive() throws IOException {
    var documentBodyToCreate = searchDocumentBody();
    var documentBodyToDelete = searchDocumentBodyToDelete();
    var bulkResponse = mock(BulkResponse.class);
    var bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);

    when(bulkResponse.hasFailures()).thenReturn(false);
    when(restHighLevelClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = resourceRepository.indexResources(List.of(documentBodyToCreate, documentBodyToDelete));

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(2).satisfies(requests -> {
      assertThat(requests.get(0)).isInstanceOf(IndexRequest.class);
      assertThat(requests.get(1)).isInstanceOf(DeleteRequest.class);
    });
  }

  @Test
  void indexResources_positive_emptyList() {
    var response = resourceRepository.indexResources(emptyList());
    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_negative_bulkFail() throws IOException {
    var documentBody = searchDocumentBody();
    var bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.hasFailures()).thenReturn(true);
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = resourceRepository.indexResources(singletonList(documentBody));
    assertThat(response).isEqualTo(getErrorIndexOperationResponse(null));
  }

  @Test
  void indexResources_negative_throwsException() throws IOException {
    var documentBody = searchDocumentBody();
    var documentBodies = singletonList(documentBody);
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT))).thenThrow(new IOException("err"));

    assertThatThrownBy(() -> resourceRepository.indexResources(documentBodies))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=index_name, type=bulkApi, message: err]");
  }

  @Test
  void deleteDocumentsByTenantId_positive() throws IOException {
    var deleteByQueryRequestCaptor = ArgumentCaptor.forClass(DeleteByQueryRequest.class);
    var bulkByScrollResponse = mock(BulkByScrollResponse.class);

    when(bulkByScrollResponse.getDeleted()).thenReturn(5L);
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT_ID)).thenReturn(INDEX_NAME);
    when(restHighLevelClient.deleteByQuery(deleteByQueryRequestCaptor.capture(), eq(DEFAULT)))
      .thenReturn(bulkByScrollResponse);

    var response = resourceRepository.deleteConsortiumDocumentsByTenantId(ResourceType.INSTANCE, TENANT_ID);

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
    var capturedRequest = deleteByQueryRequestCaptor.getValue();
    assertThat(capturedRequest.indices()).containsExactly(INDEX_NAME);

    // Verify query structure - should have bool query with must and must_not clauses
    var query = capturedRequest.getSearchRequest().source().query();
    assertThat(query).isInstanceOf(BoolQueryBuilder.class);
    var boolQuery = (BoolQueryBuilder) query;
    assertThat(boolQuery.must()).hasSize(1);
    assertThat(boolQuery.mustNot()).hasSize(1);
  }

  @Test
  void deleteDocumentsByTenantId_negative_throwsException() throws IOException {
    when(restHighLevelClient.deleteByQuery(any(DeleteByQueryRequest.class), eq(DEFAULT)))
      .thenThrow(new IOException("delete error"));
    when(indexNameProvider.getIndexName(ResourceType.INSTANCE, TENANT_ID)).thenReturn(INDEX_NAME);

    assertThatThrownBy(() -> resourceRepository.deleteConsortiumDocumentsByTenantId(ResourceType.INSTANCE, TENANT_ID))
      .isInstanceOf(SearchOperationException.class)
      .hasCauseExactlyInstanceOf(IOException.class)
      .hasMessage("Failed to perform elasticsearch request "
        + "[index=folio_instance_test_tenant, type=deleteByQueryApi, message: delete error]");
  }
}
