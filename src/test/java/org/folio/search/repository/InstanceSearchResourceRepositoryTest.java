package org.folio.search.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.model.types.IndexingDataFormat.JSON;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.io.IOException;
import java.util.List;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.model.index.InstanceSearchDocumentBody;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.index.VersionType;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceSearchResourceRepositoryTest {

  @InjectMocks
  private InstanceSearchResourceRepository repository;
  @Mock
  private RestHighLevelClient restHighLevelClient;

  @Test
  void indexResources_appliesExternalVersioningToBulkIndexAndDelete() throws IOException {
    var bulkResponse = mock(BulkResponse.class);
    var bulkRequestCaptor = ArgumentCaptor.forClass(BulkRequest.class);
    when(bulkResponse.hasFailures()).thenReturn(false);
    when(restHighLevelClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = repository.indexResources(List.of(
      document("instance-1", INDEX, 42L),
      document("instance-1", DELETE, 43L)
    ));

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(2).satisfies(requests -> {
      assertIndexRequest(requests.get(0), 42L);
      assertDeleteRequest(requests.get(1), 43L);
    });
  }

  @Test
  void indexResources_treatsVersionConflictsAsExpected() throws IOException {
    var bulkResponse = mock(BulkResponse.class);
    var item = mock(BulkItemResponse.class);
    when(bulkResponse.hasFailures()).thenReturn(true);
    when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] {item});
    when(item.isFailed()).thenReturn(true);
    when(item.getFailureMessage()).thenReturn("version_conflict_engine_exception: stale write");
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT))).thenReturn(bulkResponse);

    var response = repository.indexResources(List.of(document("instance-1", INDEX, 42L)));

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_returnsErrorForNonVersionConflictFailures() throws IOException {
    var bulkResponse = mock(BulkResponse.class);
    var item = mock(BulkItemResponse.class);
    when(bulkResponse.hasFailures()).thenReturn(true);
    when(bulkResponse.getItems()).thenReturn(new BulkItemResponse[] {item});
    when(item.isFailed()).thenReturn(true);
    when(item.getFailureMessage()).thenReturn("mapper_parsing_exception: broken payload");
    when(restHighLevelClient.bulk(any(BulkRequest.class), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.buildFailureMessage()).thenReturn("mapper_parsing_exception: broken payload");

    var response = repository.indexResources(List.of(document("instance-1", INDEX, 42L)));

    assertThat(response).isEqualTo(getErrorIndexOperationResponse("mapper_parsing_exception: broken payload"));
  }

  private static InstanceSearchDocumentBody document(String id, org.folio.search.model.types.IndexActionType action,
                                                     long sourceVersion) {
    return InstanceSearchDocumentBody.of(
      new BytesArray("{\"id\":\"" + id + "\"}"),
      JSON,
      new ResourceEvent().id(id).tenant("tenant").resourceName("instance_search"),
      action,
      "folio_instance_search_tenant",
      "instance-1",
      sourceVersion
    );
  }

  private static void assertIndexRequest(DocWriteRequest<?> request, long expectedVersion) {
    assertThat(request).isInstanceOf(IndexRequest.class);
    var indexRequest = (IndexRequest) request;
    assertThat(indexRequest.index()).isEqualTo("folio_instance_search_tenant");
    assertThat(indexRequest.id()).isEqualTo("instance-1");
    assertThat(indexRequest.routing()).isEqualTo("instance-1");
    assertThat(indexRequest.version()).isEqualTo(expectedVersion);
    assertThat(indexRequest.versionType()).isEqualTo(VersionType.EXTERNAL);
  }

  private static void assertDeleteRequest(DocWriteRequest<?> request, long expectedVersion) {
    assertThat(request).isInstanceOf(DeleteRequest.class);
    var deleteRequest = (DeleteRequest) request;
    assertThat(deleteRequest.index()).isEqualTo("folio_instance_search_tenant");
    assertThat(deleteRequest.id()).isEqualTo("instance-1");
    assertThat(deleteRequest.routing()).isEqualTo("instance-1");
    assertThat(deleteRequest.version()).isEqualTo(expectedVersion);
    assertThat(deleteRequest.versionType()).isEqualTo(VersionType.EXTERNAL);
  }
}
