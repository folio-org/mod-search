package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.searchDocumentBody;
import static org.folio.search.utils.TestUtils.searchDocumentBodyToDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import java.util.List;
import org.folio.search.exception.SearchOperationException;
import org.folio.search.service.consortia.TenantProvider;
import org.folio.search.support.base.TenantConfig;
import org.folio.spring.test.type.UnitTest;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@UnitTest
@ExtendWith(MockitoExtension.class)
@SpringBootTest(classes = TenantConfig.class)
class PrimaryResourceRepositoryTest {

  @InjectMocks
  private PrimaryResourceRepository resourceRepository;
  @Mock
  private RestHighLevelClient restHighLevelClient;
  @Autowired
  private TenantProvider tenantProvider;

  @BeforeEach
  void setUp() {
    resourceRepository.setTenantProvider(tenantProvider);
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
        + "[index=folio_test-resource_" + tenantProvider.getTenant(TENANT_ID) + ", type=bulkApi, message: err]");
  }
}
