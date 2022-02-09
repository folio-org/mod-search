package org.folio.search.repository;

import static java.util.Collections.emptyMap;
import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.elasticsearch.common.xcontent.DeprecationHandler.IGNORE_DEPRECATIONS;
import static org.elasticsearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.folio.search.model.Pair.pair;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.NAMED_XCONTENT_REGISTRY;
import static org.folio.search.utils.TestUtils.aggregationsFromJson;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import lombok.SneakyThrows;
import org.elasticsearch.action.DocWriteRequest;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetRequest.Item;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.fetch.subphase.FetchSourceContext;
import org.folio.search.domain.dto.ResourceEventType;
import org.folio.search.model.Pair;
import org.folio.search.model.SimpleResourceRequest;
import org.folio.search.model.index.SearchDocumentBody;
import org.folio.search.utils.BrowseUtils;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceSubjectRepositoryTest {

  private static final String INSTANCE_INDEX = getIndexName(INSTANCE_RESOURCE, TENANT_ID);

  @InjectMocks private InstanceSubjectRepository repository;
  @Mock private IndexRepository indexRepository;
  @Mock private SearchRepository searchRepository;
  @Mock private RestHighLevelClient elasticsearchClient;
  @Captor private ArgumentCaptor<BulkRequest> bulkRequestCaptor;
  @Captor private ArgumentCaptor<MultiGetRequest> multiGetRequestCaptor;

  @BeforeEach
  void setUp() {
    repository.setElasticsearchClient(elasticsearchClient);
  }

  @Test
  void indexResources_positive_onlyIndexEvents() throws IOException {
    var document = subjectDocumentBody();
    var bulkResponse = mock(BulkResponse.class);

    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);
    when(bulkResponse.hasFailures()).thenReturn(false);

    var actual = repository.indexResources(List.of(document));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    assertThat(bulkRequestCaptor.getValue().requests()).hasSize(1).satisfies(requests ->
      assertThat(requests.get(0)).isInstanceOf(IndexRequest.class));
  }

  @Test
  void indexResources_positive_deleteSubjects() throws IOException {
    var subject1 = "java";
    var subject2 = "scala";

    doNothing().when(indexRepository).refreshIndices(INSTANCE_INDEX);
    when(elasticsearchClient.mget(multiGetRequestCaptor.capture(), eq(DEFAULT)))
      .thenReturn(searchResponseForSubjectIds(mapOf(subject1, pair(123L, 1L), subject2, pair(172L, 2L))));
    mockCountSearchResponse(mapOf(subject1, null, subject2, 3));

    var bulkResponse = bulkResponse(mapOf(subject1, null));
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);

    var documents = List.of(searchDocumentBodyToDelete(subject1), searchDocumentBodyToDelete(subject2));
    var actual = repository.indexResources(documents);

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    checkCalledBulkRequest(List.of(new BulkRequest().add(deleteRequest(subject1, 123L, 1L))));
    checkCalledMultiGetRequests(List.of(multiGetRequest(subject1, subject2)));
  }

  @Test
  void indexResources_positive_subjectNotFoundById() throws IOException {
    var subject = "java";
    doNothing().when(indexRepository).refreshIndices(INSTANCE_INDEX);
    when(elasticsearchClient.mget(multiGetRequestCaptor.capture(), eq(DEFAULT))).thenReturn(
      searchResponseForSubjectIds(emptyMap()));
    mockCountSearchResponse(mapOf(subject, null));

    var actual = repository.indexResources(List.of(searchDocumentBodyToDelete(subject)));

    verifyNoMoreInteractions(elasticsearchClient);
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_positive_subjectCountMoreThanZero() throws IOException {
    var subject = "java";
    doNothing().when(indexRepository).refreshIndices(INSTANCE_INDEX);
    when(elasticsearchClient.mget(multiGetRequestCaptor.capture(), eq(DEFAULT))).thenReturn(
      searchResponseForSubjectIds(mapOf(subject, pair(50L, 2L))));
    mockCountSearchResponse(mapOf(subject, 10));

    var actual = repository.indexResources(List.of(searchDocumentBodyToDelete(subject)));

    verifyNoMoreInteractions(elasticsearchClient);
    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
  }

  @Test
  void indexResources_positive_retryFailed() throws IOException {
    var subject = "java";

    doNothing().when(indexRepository).refreshIndices(INSTANCE_INDEX);
    when(elasticsearchClient.mget(multiGetRequestCaptor.capture(), eq(DEFAULT))).thenReturn(
      searchResponseForSubjectIds(mapOf(subject, pair(123L, 1L))),
      searchResponseForSubjectIds(mapOf(subject, pair(124L, 2L))),
      searchResponseForSubjectIds(mapOf(subject, pair(125L, 3L))));
    mockCountSearchResponse(mapOf(subject, null));

    var bulkResponse = bulkResponse(mapOf(subject, "Optimistic locking error"));
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);

    var actual = repository.indexResources(List.of(searchDocumentBodyToDelete(subject)));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    checkCalledBulkRequest(List.of(
      new BulkRequest().add(deleteRequest(subject, 123L, 1L)),
      new BulkRequest().add(deleteRequest(subject, 124L, 2L)),
      new BulkRequest().add(deleteRequest(subject, 125L, 3L))));

    checkCalledMultiGetRequests(List.of(multiGetRequest(subject), multiGetRequest(subject), multiGetRequest(subject)));
  }

  @Test
  void indexResources_positive_thirdAttemptIsPositive() throws IOException {
    var subject = "java";

    doNothing().when(indexRepository).refreshIndices(INSTANCE_INDEX);
    when(elasticsearchClient.mget(multiGetRequestCaptor.capture(), eq(DEFAULT))).thenReturn(
      searchResponseForSubjectIds(mapOf(subject, pair(123L, 1L))),
      searchResponseForSubjectIds(mapOf(subject, pair(124L, 2L))),
      searchResponseForSubjectIds(mapOf(subject, pair(125L, 3L))));
    mockCountSearchResponse(mapOf(subject, null));

    var errorBulkResponse = bulkResponse(mapOf(subject, "Optimistic locking error"));
    var positiveBulkResponse = bulkResponse(mapOf(subject, null));
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(
      errorBulkResponse, errorBulkResponse, positiveBulkResponse);

    var actual = repository.indexResources(List.of(searchDocumentBodyToDelete(subject)));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    checkCalledBulkRequest(List.of(
      new BulkRequest().add(deleteRequest(subject, 123L, 1L)),
      new BulkRequest().add(deleteRequest(subject, 124L, 2L)),
      new BulkRequest().add(deleteRequest(subject, 125L, 3L))));

    checkCalledMultiGetRequests(List.of(multiGetRequest(subject), multiGetRequest(subject), multiGetRequest(subject)));
  }

  @Test
  void indexResources_positive_deleteSubjectsConsequently() throws IOException {
    var s1 = "java";
    var s2 = "python";
    var s3 = "js";

    doNothing().when(indexRepository).refreshIndices(INSTANCE_INDEX);
    when(elasticsearchClient.mget(multiGetRequestCaptor.capture(), eq(DEFAULT))).thenReturn(
      searchResponseForSubjectIds(mapOf(s1, pair(11L, 11L), s2, pair(21L, 21L), s3, pair(31L, 31L))),
      searchResponseForSubjectIds(mapOf(s1, pair(12L, 12L), s2, pair(22L, 22L))),
      searchResponseForSubjectIds(mapOf(s1, pair(13L, 13L))));
    mockCountSearchResponse(mapOf(s1, null, s2, null, s3, null));

    var bulkResponse1 = bulkResponse(mapOf(s1, "error", s2, "error", s3, null));
    var bulkResponse2 = bulkResponse(mapOf(s1, "error", s2, null));
    var bulkResponse3 = bulkResponse(mapOf(s1, null));
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT)))
      .thenReturn(bulkResponse1, bulkResponse2, bulkResponse3);

    var actual = repository.indexResources(List.of(
      searchDocumentBodyToDelete(s1), searchDocumentBodyToDelete(s2), searchDocumentBodyToDelete(s3)));

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    checkCalledBulkRequest(List.of(
      new BulkRequest().add(deleteRequest(s1, 11L, 11L), deleteRequest(s2, 21L, 21L), deleteRequest(s3, 31L, 31L)),
      new BulkRequest().add(deleteRequest(s1, 12L, 12L), deleteRequest(s2, 22L, 22L)),
      new BulkRequest().add(deleteRequest(s1, 13L, 13L))));

    checkCalledMultiGetRequests(List.of(multiGetRequest(s1, s2, s3), multiGetRequest(s1, s2), multiGetRequest(s1)));
  }

  private static DeleteRequest deleteRequest(String subject, Long seqNo, Long primaryTerm) {
    return new DeleteRequest(getIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID))
      .id(sha256Hex(subject)).setIfSeqNo(seqNo).setIfPrimaryTerm(primaryTerm).routing(TENANT_ID);
  }

  private static BulkResponse bulkResponse(Map<String, String> subjectErrors) {
    var bulkResponse = mock(BulkResponse.class);
    var items = new ArrayList<BulkItemResponse>();

    for (var entry : subjectErrors.entrySet()) {
      var error = entry.getValue();
      var item = mock(BulkItemResponse.class);
      when(item.isFailed()).thenReturn(error != null);

      if (error != null) {
        var failure = mock(Failure.class);
        when(item.getFailure()).thenReturn(failure);
        when(failure.getId()).thenReturn(sha256Hex(entry.getKey()));
      }

      items.add(item);
    }

    when(bulkResponse.getItems()).thenReturn(items.toArray(BulkItemResponse[]::new));
    return bulkResponse;
  }

  private void mockCountSearchResponse(Map<String, Integer> subjectCounts) {
    var aggregationBuckets = jsonArray();
    subjectCounts.entrySet().stream()
      .filter(entry -> entry.getValue() != null)
      .forEach(entry -> aggregationBuckets.add(jsonObject("key", entry.getKey(), "doc_count", entry.getValue())));

    var countQueryResponse = mock(SearchResponse.class);
    var subjectsQuerySource = BrowseUtils.getSubjectCountsQuery(subjectCounts.keySet());
    var request = SimpleResourceRequest.of(INSTANCE_RESOURCE, TENANT_ID);
    when(searchRepository.search(request, subjectsQuerySource)).thenReturn(countQueryResponse);
    when(countQueryResponse.getAggregations()).thenReturn(
      aggregationsFromJson(jsonObject("sterms#subjects", jsonObject("buckets", aggregationBuckets))));
  }

  private static SearchDocumentBody subjectDocumentBody() {
    var subject = "test";
    var body = mapOf("subject", subject);
    var event = resourceEvent(sha256Hex(subject), INSTANCE_SUBJECT_RESOURCE, ResourceEventType.CREATE, body, null);
    return SearchDocumentBody.of(asJsonString(body), event, INDEX);
  }

  private static SearchDocumentBody searchDocumentBodyToDelete(String subject) {
    var body = mapOf("subject", subject);
    var event = resourceEvent(sha256Hex(subject), INSTANCE_SUBJECT_RESOURCE, ResourceEventType.DELETE, null, body);
    return SearchDocumentBody.of(asJsonString(body), event, DELETE);
  }

  private static MultiGetResponse searchResponseForSubjectIds(Map<String, Pair<Long, Long>> seqNumbers) {
    var objects = seqNumbers.entrySet().stream()
      .map(InstanceSubjectRepositoryTest::foundDocumentById)
      .toArray(Object[]::new);
    return multiGetResponseFromJson(jsonObject("docs", jsonArray(objects)));
  }

  private static ObjectNode foundDocumentById(Entry<String, Pair<Long, Long>> entry) {
    return jsonObject(
      "_id", sha256Hex(entry.getKey()),
      "_type", "_doc",
      "_version", 1,
      "_seq_no", entry.getValue().getFirst(),
      "_primary_term", entry.getValue().getSecond(),
      "_index", getIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID),
      "found", true
    );
  }

  @SneakyThrows
  public static MultiGetResponse multiGetResponseFromJson(JsonNode jsonNode) {
    var parser = jsonXContent.createParser(NAMED_XCONTENT_REGISTRY, IGNORE_DEPRECATIONS, jsonNode.toString());
    return MultiGetResponse.fromXContent(parser);
  }

  private void checkCalledBulkRequest(List<BulkRequest> expectedRequests) {
    var actualRequests = bulkRequestCaptor.getAllValues();
    assertThat(actualRequests).hasSize(expectedRequests.size());
    for (int i = 0; i < expectedRequests.size(); i++) {
      validateBulkRequest(actualRequests.get(i), expectedRequests.get(i),
        InstanceSubjectRepositoryTest::validateDeleteRequest);
    }
  }

  public static void validateBulkRequest(BulkRequest actual, BulkRequest expected,
    BiConsumer<DocWriteRequest<?>, DocWriteRequest<?>> requestValidator) {
    var actualRequests = actual.requests();
    var expectedRequests = expected.requests();
    assertThat(actualRequests.size()).isEqualTo(expectedRequests.size());
    for (int i = 0; i < expectedRequests.size(); i++) {
      var actualRequest = actualRequests.get(i);
      var expectedRequest = expectedRequests.get(i);
      assertThat(actualRequest.getClass()).isEqualTo(expectedRequest.getClass());
      requestValidator.accept(actualRequest, expectedRequest);
    }
  }

  private static void validateDeleteRequest(DocWriteRequest<?> actual, DocWriteRequest<?> expected) {
    assertThat(actual)
      .usingRecursiveComparison()
      .comparingOnlyFields("id", "routing", "ifSeqNo", "ifPrimaryTerm", "index")
      .isEqualTo(expected);
  }

  private void checkCalledMultiGetRequests(List<MultiGetRequest> expectedRequests) {
    var actualRequests = multiGetRequestCaptor.getAllValues();
    assertThat(actualRequests).hasSize(expectedRequests.size());
    for (int i = 0; i < expectedRequests.size(); i++) {
      validateMultiGetRequest(actualRequests.get(i), expectedRequests.get(i));
    }
  }

  public static void validateMultiGetRequest(MultiGetRequest actual, MultiGetRequest expected) {
    var actualRequests = actual.getItems();
    var expectedRequests = expected.getItems();
    assertThat(actualRequests.size()).isEqualTo(expectedRequests.size());
    for (int i = 0; i < expectedRequests.size(); i++) {
      var actualRequest = actualRequests.get(i);
      var expectedRequest = expectedRequests.get(i);
      assertThat(actualRequest).usingRecursiveComparison().isEqualTo(expectedRequest);
    }
  }

  private static MultiGetRequest multiGetRequest(String... subjects) {
    var multiGetRequest = new MultiGetRequest();
    var fetchSource = new FetchSourceContext(false);
    for (var subject : subjects) {
      var item = new Item(getIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID), sha256Hex(subject))
        .routing(TENANT_ID).fetchSourceContext(fetchSource);
      multiGetRequest.add(item);
    }
    return multiGetRequest;
  }
}
