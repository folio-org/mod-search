package org.folio.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.search.configuration.RetryTemplateConfiguration.ITEM_INDEXING_RETRY_TEMPLATE_NAME;
import static org.folio.search.domain.dto.ResourceEventType.CREATE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE;
import static org.folio.search.domain.dto.ResourceEventType.DELETE_ALL;
import static org.folio.search.model.types.ResourceType.INSTANCE;
import static org.folio.search.model.types.ResourceType.ITEM;
import static org.folio.search.utils.SearchResponseHelper.getErrorIndexOperationResponse;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.support.TestConstants.CENTRAL_TENANT_ID;
import static org.folio.support.TestConstants.RESOURCE_ID;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.resourceEvent;
import static org.folio.support.utils.TestUtils.searchDocumentBody;
import static org.folio.support.utils.TestUtils.searchDocumentBodyToDelete;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;
import static org.opensearch.index.query.QueryBuilders.termsQuery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import org.folio.search.domain.dto.FolioIndexOperationResponse;
import org.folio.search.domain.dto.ResourceEvent;
import org.folio.search.exception.IndexingFailureException;
import org.folio.search.integration.message.FolioMessageBatchProcessor;
import org.folio.search.repository.PrimaryResourceRepository;
import org.folio.search.service.consortium.ConsortiumTenantProvider;
import org.folio.search.service.consortium.SharedInstanceResolver;
import org.folio.search.service.converter.MultiTenantSearchDocumentConverter;
import org.folio.search.utils.IndexingFailureUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest.OpType;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkItemResponse.Failure;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilder;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ItemIndexServiceTest {

  private static final int MAX_RETRIES = 2;
  private static final String MAPPING_ERROR =
    "OpenSearch exception [type=mapper_parsing_exception, reason=failed to parse field [statusName]]";
  private static final String NESTED_ERROR =
    "OpenSearch exception [type=illegal_argument_exception, reason=Can't parse [keyword] value]";
  private static final String REJECTED_ERROR =
    "OpenSearch exception [type=rejected_execution_exception, reason=rejected execution]";

  @Spy
  private final FolioMessageBatchProcessor batchProcessor = new FolioMessageBatchProcessor(
    Map.of(ITEM_INDEXING_RETRY_TEMPLATE_NAME, new RetryTemplate(RetryPolicy.builder()
      .maxRetries(MAX_RETRIES)
      .delay(Duration.ofMillis(1))
      .predicate(IndexingFailureUtils::isRetryable)
      .build())),
    new RetryTemplate());
  @Mock
  private PrimaryResourceRepository primaryResourceRepository;
  @Mock
  private MultiTenantSearchDocumentConverter searchDocumentConverter;
  @Mock
  private EgressExecutionContextService executionService;
  @Mock
  private FailedIndexingEventService failedIndexingEventService;
  @Mock
  private SharedInstanceResolver sharedInstanceResolver;
  @Mock
  private ConsortiumTenantProvider consortiumTenantProvider;
  @InjectMocks
  private ItemIndexService itemIndexService;
  @Captor
  private ArgumentCaptor<Collection<ResourceEvent>> eventsCaptor;
  @Captor
  private ArgumentCaptor<Exception> exceptionCaptor;
  @Captor
  private ArgumentCaptor<QueryBuilder> queryCaptor;

  @BeforeEach
  void setUp() {
    lenient().doAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call())
      .when(executionService).execute(anyString(), any(Callable.class));
  }

  @Test
  void indexItemEvents_positive_indexesOnlyItemEventsWithTenantScopedId() {
    var itemNew = new LinkedHashMap<String, Object>(mapOf("id", RESOURCE_ID));
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, CREATE, itemNew, null);
    var instanceEvent = resourceEvent(randomId(), INSTANCE, CREATE, mapOf("id", randomId()), null);
    var searchBody = searchDocumentBody();
    when(searchDocumentConverter.convert(eventsCaptor.capture()))
      .thenReturn(mapOf(ITEM.getName(), List.of(searchBody)));
    when(primaryResourceRepository.executeBulk(List.of(searchBody))).thenReturn(bulkResponse());

    var response = itemIndexService.indexItemEvents(List.of(itemEvent, instanceEvent));

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(eventsCaptor.getValue()).singleElement().satisfies(event -> {
      assertThat(event.getId()).isEqualTo(RESOURCE_ID + "|" + TENANT_ID);
      assertThat(event.getNew()).isNotSameAs(itemNew).isEqualTo(itemNew);
    });
    assertThat(itemEvent.getId()).isEqualTo(RESOURCE_ID);
    assertThat(itemEvent.getNew()).isSameAs(itemNew);
    verify(executionService).execute(eq(TENANT_ID), any(Callable.class));
    verifyNoInteractions(failedIndexingEventService);
  }

  @Test
  void indexItemEvents_positive_deleteEvent() {
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, DELETE);
    var searchBody = searchDocumentBodyToDelete();
    when(searchDocumentConverter.convert(eventsCaptor.capture()))
      .thenReturn(mapOf(ITEM.getName(), List.of(searchBody)));
    when(primaryResourceRepository.executeBulk(List.of(searchBody))).thenReturn(bulkResponse());

    itemIndexService.indexItemEvents(List.of(itemEvent));

    assertThat(eventsCaptor.getValue()).singleElement()
      .satisfies(event -> assertThat(event.getId()).isEqualTo(RESOURCE_ID + "|" + TENANT_ID));
    verifyNoInteractions(failedIndexingEventService);
  }

  @Test
  void indexItemEvents_positive_noItemEvents() {
    var instanceEvent = resourceEvent(randomId(), INSTANCE, CREATE, mapOf("id", randomId()), null);

    itemIndexService.indexItemEvents(List.of(instanceEvent));

    verifyNoInteractions(searchDocumentConverter, primaryResourceRepository, failedIndexingEventService);
  }

  @Test
  void indexItemEvents_positive_noSharedInstancesLeavesFlagUnset() {
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID, "instanceId", randomId()), null);
    mockSuccessfulIndexing();

    itemIndexService.indexItemEvents(List.of(itemEvent));

    assertThat(eventsCaptor.getValue()).singleElement()
      .satisfies(event -> assertThat(((Map<?, ?>) event.getNew()).containsKey("shared")).isFalse());
  }

  @Test
  void indexItemEvents_positive_setsSharedFlagOnlyForItemsOfSharedInstances() {
    var sharedInstanceId = randomId();
    var localInstanceId = randomId();
    when(sharedInstanceResolver.findSharedInstanceIds(TENANT_ID, Set.of(sharedInstanceId, localInstanceId)))
      .thenReturn(Set.of(sharedInstanceId));
    mockSuccessfulIndexing();
    var sharedItem = resourceEvent(randomId(), ITEM, CREATE, mapOf("instanceId", sharedInstanceId), null);
    var localItem = resourceEvent(randomId(), ITEM, CREATE, mapOf("instanceId", localInstanceId), null);

    itemIndexService.indexItemEvents(List.of(sharedItem, localItem));

    assertThat(eventsCaptor.getValue()).extracting(event -> (Object) ((Map<?, ?>) event.getNew()).get("shared"))
      .containsExactly(true, null);
    assertThat(((Map<?, ?>) sharedItem.getNew()).containsKey("shared")).isFalse();
  }

  @Test
  void indexItemEvents_positive_centralInstanceCreateMarksItemsShared() {
    var instanceId = randomId();
    var instanceEvent = resourceEvent(instanceId, INSTANCE, CREATE, mapOf("id", instanceId), null)
      .tenant(CENTRAL_TENANT_ID);
    when(consortiumTenantProvider.isCentralTenant(CENTRAL_TENANT_ID)).thenReturn(true);
    when(primaryResourceRepository.updateByQuery(queryCaptor.capture(), eq(ITEM), any(), eq(CENTRAL_TENANT_ID)))
      .thenReturn(getSuccessIndexOperationResponse());

    var response = itemIndexService.indexItemEvents(List.of(instanceEvent));

    assertThat(response).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(queryCaptor.getValue()).isEqualTo(boolQuery()
      .filter(termsQuery("instanceId", List.of(instanceId)))
      .mustNot(termQuery("shared", true)));
  }

  @Test
  void indexItemEvents_positive_memberInstanceCreateDoesNotMarkItemsShared() {
    var instanceEvent = resourceEvent(randomId(), INSTANCE, CREATE, mapOf("id", randomId()), null);
    when(consortiumTenantProvider.isCentralTenant(TENANT_ID)).thenReturn(false);

    itemIndexService.indexItemEvents(List.of(instanceEvent));

    verifyNoInteractions(primaryResourceRepository);
  }

  @Test
  void indexItemEvents_negative_markItemsSharedFailureIsStored() {
    var instanceEvent = resourceEvent(randomId(), INSTANCE, CREATE, mapOf("id", randomId()), null)
      .tenant(CENTRAL_TENANT_ID);
    when(consortiumTenantProvider.isCentralTenant(CENTRAL_TENANT_ID)).thenReturn(true);
    when(primaryResourceRepository.updateByQuery(any(), eq(ITEM), any(), eq(CENTRAL_TENANT_ID)))
      .thenReturn(getErrorIndexOperationResponse("update failed"));

    var response = itemIndexService.indexItemEvents(List.of(instanceEvent));

    assertThat(response.getStatus()).isEqualTo(FolioIndexOperationResponse.StatusEnum.ERROR);
    verify(failedIndexingEventService).save(any(), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).hasMessageContaining("update failed");
  }

  private void mockSuccessfulIndexing() {
    var searchBody = searchDocumentBody();
    when(searchDocumentConverter.convert(eventsCaptor.capture()))
      .thenReturn(mapOf(ITEM.getName(), List.of(searchBody)));
    when(primaryResourceRepository.executeBulk(List.of(searchBody))).thenReturn(bulkResponse());
  }

  @Test
  void indexItemEvents_negative_clientErrorIsStoredWithoutRetry() {
    var cause = new OpenSearchStatusException(MAPPING_ERROR, RestStatus.BAD_REQUEST,
      new OpenSearchException(NESTED_ERROR));
    mockBulkResponse(bulkResponse(failedItem(0, cause)));
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID), null);

    itemIndexService.indexItemEvents(List.of(itemEvent));

    verify(primaryResourceRepository).executeBulk(any());
    verify(failedIndexingEventService).save(eq(itemEvent), exceptionCaptor.capture());
    var failure = (IndexingFailureException) exceptionCaptor.getValue();
    assertThat(failure.getStatus()).isEqualTo(400);
    assertThat(failure.getErrorType()).isEqualTo("mapper_parsing_exception");
    assertThat(failure.getMessage())
      .contains("index [item-index]", "id [doc-0]", "status [400]", MAPPING_ERROR, NESTED_ERROR);
  }

  @Test
  void indexItemEvents_negative_tooManyRequestsIsRetriedThenStored() {
    mockBulkResponse(bulkResponse(
      failedItem(0, new OpenSearchStatusException(REJECTED_ERROR, RestStatus.TOO_MANY_REQUESTS))));
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, CREATE, mapOf("id", RESOURCE_ID), null);

    itemIndexService.indexItemEvents(List.of(itemEvent));

    verify(primaryResourceRepository, times(MAX_RETRIES + 1)).executeBulk(any());
    verify(failedIndexingEventService).save(eq(itemEvent), exceptionCaptor.capture());
    assertThat(IndexingFailureUtils.getStatus(exceptionCaptor.getValue())).isEqualTo(429);
  }

  @Test
  void indexItemEvents_negative_onlyFailedEventIsStoredAfterOneByOneFallback() {
    final var okEvent = resourceEvent(randomId(), ITEM, CREATE, mapOf("id", "ok"), null);
    var badEvent = resourceEvent(randomId(), ITEM, CREATE, mapOf("id", "bad"), null);
    var okBody = searchDocumentBody();
    var badBody = searchDocumentBody("{\"id\":\"bad\"}");
    var badResponse = bulkResponse(
      failedItem(0, new OpenSearchStatusException(MAPPING_ERROR, RestStatus.BAD_REQUEST)));
    when(searchDocumentConverter.convert(anyCollection())).thenAnswer(invocation -> {
      var bodies = new ArrayList<Object>();
      for (var event : invocation.<Collection<ResourceEvent>>getArgument(0)) {
        bodies.add(event.getId().startsWith(badEvent.getId()) ? badBody : okBody);
      }
      return mapOf(ITEM.getName(), bodies);
    });
    when(primaryResourceRepository.executeBulk(List.of(okBody, badBody))).thenReturn(badResponse);
    when(primaryResourceRepository.executeBulk(List.of(okBody))).thenReturn(bulkResponse());
    when(primaryResourceRepository.executeBulk(List.of(badBody))).thenReturn(badResponse);

    var response = itemIndexService.indexItemEvents(List.of(okEvent, badEvent));

    assertThat(response.getErrorMessage())
      .isEqualTo("Failed to index 1 of 2 item events, failures are stored in failed_indexing_event [ids: %s]"
        .formatted(badEvent.getId()));
    verify(failedIndexingEventService).save(eq(badEvent), any(IndexingFailureException.class));
    verify(failedIndexingEventService, times(1)).save(any(), any());
  }

  @Test
  void indexItemEvents_positive_deleteAllRemovesTenantItems() {
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, DELETE_ALL);
    when(primaryResourceRepository.deleteResourceByTenantId(ITEM, TENANT_ID))
      .thenReturn(getSuccessIndexOperationResponse());

    itemIndexService.indexItemEvents(List.of(itemEvent));

    verify(primaryResourceRepository).deleteResourceByTenantId(ITEM, TENANT_ID);
    verifyNoInteractions(searchDocumentConverter, failedIndexingEventService);
  }

  @Test
  void indexItemEvents_negative_deleteAllFailureIsRetriedThenStored() {
    var itemEvent = resourceEvent(RESOURCE_ID, ITEM, DELETE_ALL);
    when(primaryResourceRepository.deleteResourceByTenantId(ITEM, TENANT_ID))
      .thenReturn(getErrorIndexOperationResponse("version conflict"));

    itemIndexService.indexItemEvents(List.of(itemEvent));

    verify(primaryResourceRepository, times(MAX_RETRIES + 1)).deleteResourceByTenantId(ITEM, TENANT_ID);
    verify(failedIndexingEventService).save(eq(itemEvent), exceptionCaptor.capture());
    assertThat(exceptionCaptor.getValue()).hasMessageContaining("version conflict");
  }

  private void mockBulkResponse(BulkResponse bulkResponse) {
    var searchBody = searchDocumentBody();
    when(searchDocumentConverter.convert(anyCollection())).thenReturn(mapOf(ITEM.getName(), List.of(searchBody)));
    when(primaryResourceRepository.executeBulk(List.of(searchBody))).thenReturn(bulkResponse);
  }

  private static BulkResponse bulkResponse(BulkItemResponse... items) {
    return new BulkResponse(items, 1L);
  }

  private static BulkItemResponse failedItem(int position, Exception cause) {
    return new BulkItemResponse(position, OpType.INDEX, new Failure("item-index", "doc-" + position, cause));
  }
}
