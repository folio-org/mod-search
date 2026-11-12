package org.folio.search.service.indexrebuild.records;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.IndexingFailureException;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.model.types.IndexActionType;
import org.folio.search.model.types.IndexingDataFormat;
import org.folio.search.model.types.ResourceType;
import org.folio.search.repository.IndexNameProvider;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.service.indexrebuild.item.ItemRecordsRebuildStrategy;
import org.folio.search.service.indexrebuild.item.ItemSharedFlagResolver;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RecordsIndexWriterTest {

  private static final String INDEX = "item_index";
  private static final String ITEM_ID = "item-id";

  @Mock
  private MultiTenantSearchDocumentConverter documentConverter;
  @Mock
  private ItemSharedFlagResolver sharedFlagResolver;
  @Mock
  private IndexNameProvider indexNameProvider;
  @Mock
  private RestHighLevelClient elasticsearchClient;
  @Captor
  private ArgumentCaptor<Collection<ResourceEvent>> eventsCaptor;
  @Captor
  private ArgumentCaptor<BulkRequest> bulkCaptor;
  private RecordsRebuildStrategy strategy;
  private RecordsIndexWriter writer;

  @BeforeEach
  void setUp() {
    strategy = new ItemRecordsRebuildStrategy(sharedFlagResolver, null);
    writer = new RecordsIndexWriter(documentConverter, indexNameProvider, elasticsearchClient,
      new RetryTemplate(RetryPolicy.builder().predicate(throwable -> false).build()));
  }

  @Test
  void write_positive_createsDocumentsWithTenantScopedIdAndSharedFlag() throws Exception {
    mockConversion();
    when(sharedFlagResolver.isShared(any(), any())).thenReturn(true);
    when(elasticsearchClient.bulk(bulkCaptor.capture(), any())).thenReturn(response());

    writer.write(strategy, TENANT_ID, List.of(Map.of("id", ITEM_ID)));

    assertThat(eventsCaptor.getValue()).singleElement().satisfies(event -> {
      assertThat(event.getId()).isEqualTo(ITEM_ID + "|" + TENANT_ID);
      assertThat(event.getTenant()).isEqualTo(TENANT_ID);
      var newObject = (Map<?, ?>) event.getNew();
      assertThat(newObject).isNotNull();
      assertThat((boolean) newObject.get("shared")).isTrue();
    });
    var request = (IndexRequest) bulkCaptor.getValue().requests().getFirst();
    assertThat(request.opType()).isEqualTo(DocWriteRequest.OpType.CREATE);
  }

  @Test
  void write_positive_conflictsAreNotFailures() throws Exception {
    mockConversion();
    when(elasticsearchClient.bulk(any(), any())).thenReturn(response(failure(RestStatus.CONFLICT)));

    assertThatCode(() -> writer.write(strategy, TENANT_ID, List.of(Map.of("id", ITEM_ID)))).doesNotThrowAnyException();
  }

  @Test
  void write_negative_otherFailuresAreThrown() throws Exception {
    mockConversion();
    when(elasticsearchClient.bulk(any(), any()))
      .thenReturn(response(failure(RestStatus.CONFLICT), failure(RestStatus.BAD_REQUEST)));
    var records = List.<Map<String, Object>>of(Map.of("id", ITEM_ID));

    assertThatThrownBy(() -> writer.write(strategy, TENANT_ID, records))
      .isInstanceOf(IndexingFailureException.class)
      .hasMessageContaining("status [400]");
  }

  @Test
  void write_positive_emptyBatchIsSkipped() {
    writer.write(strategy, TENANT_ID, List.of());

    verifyNoInteractions(documentConverter, elasticsearchClient);
  }

  @Test
  void delete_positive_removesDocumentsByTenantScopedId() throws Exception {
    when(indexNameProvider.getIndexName(ResourceType.ITEM, TENANT_ID)).thenReturn(INDEX);
    when(elasticsearchClient.bulk(bulkCaptor.capture(), any())).thenReturn(response());

    writer.delete(strategy, TENANT_ID, List.of(ITEM_ID));

    assertThat(bulkCaptor.getValue().requests().getFirst().id()).isEqualTo(ITEM_ID + "|" + TENANT_ID);
  }

  private void mockConversion() {
    var event = new ResourceEvent().id(ITEM_ID).tenant(TENANT_ID).resourceName(ResourceType.ITEM.getName());
    var document = SearchDocumentBody.of(new BytesArray("{}"), IndexingDataFormat.JSON, event, IndexActionType.INDEX);
    when(documentConverter.convert(eventsCaptor.capture())).thenReturn(Map.of("item", List.of(document)));
    when(indexNameProvider.getIndexName(any(SearchDocumentBody.class))).thenReturn(INDEX);
  }

  private static BulkResponse response(BulkItemResponse... items) {
    return new BulkResponse(items, 0);
  }

  private static BulkItemResponse failure(RestStatus status) {
    return new BulkItemResponse(0, DocWriteRequest.OpType.CREATE,
      new BulkItemResponse.Failure(INDEX, ITEM_ID, new IllegalStateException("error"), status));
  }
}
