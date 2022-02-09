package org.folio.search.repository;

import static org.apache.commons.codec.digest.DigestUtils.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.model.types.IndexActionType.DELETE;
import static org.folio.search.model.types.IndexActionType.INDEX;
import static org.folio.search.repository.InstanceSubjectRepository.OPTIMISTIC_LOCKING_REQUEST_OPTIONS;
import static org.folio.search.utils.JsonUtils.jsonArray;
import static org.folio.search.utils.JsonUtils.jsonObject;
import static org.folio.search.utils.SearchResponseHelper.getSuccessIndexOperationResponse;
import static org.folio.search.utils.SearchUtils.INSTANCE_RESOURCE;
import static org.folio.search.utils.SearchUtils.INSTANCE_SUBJECT_RESOURCE;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.aggregationsFromJson;
import static org.folio.search.utils.TestUtils.asJsonString;
import static org.folio.search.utils.TestUtils.mapOf;
import static org.folio.search.utils.TestUtils.resourceEvent;
import static org.folio.search.utils.TestUtils.searchResponseFromJson;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkItemResponse.Failure;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.folio.search.configuration.properties.SearchConfigurationProperties;
import org.folio.search.configuration.properties.SearchConfigurationProperties.IndexingSettings;
import org.folio.search.configuration.properties.SearchConfigurationProperties.InstanceSubjectsIndexingSettings;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class InstanceSubjectRepositoryTest {

  @Spy private SearchConfigurationProperties searchConfigurationProperties = searchConfigurationProperties();

  @InjectMocks private InstanceSubjectRepository repository;
  @Mock private IndexRepository indexRepository;
  @Mock private SearchRepository searchRepository;
  @Mock private RestHighLevelClient elasticsearchClient;
  @Captor private ArgumentCaptor<BulkRequest> bulkRequestCaptor;

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

    doNothing().when(indexRepository).refreshIndex(getElasticsearchIndexName(INSTANCE_RESOURCE, TENANT_ID));
    when(elasticsearchClient.search(subjectIdsRequest(subject1, subject2), OPTIMISTIC_LOCKING_REQUEST_OPTIONS))
      .thenReturn(searchResponseForSubjectIds(mapOf(subject1, Pair.of(123L, 1L), subject2, Pair.of(172L, 2L))));
    mockCountSearchResponse(mapOf(subject1, 0, subject2, 3));

    var bulkResponse = bulkResponse(mapOf(subject1, null));
    when(elasticsearchClient.bulk(bulkRequestCaptor.capture(), eq(DEFAULT))).thenReturn(bulkResponse);

    var documents = List.of(searchDocumentBodyToDelete(subject1), searchDocumentBodyToDelete(subject2));
    var actual = repository.indexResources(documents);

    assertThat(actual).isEqualTo(getSuccessIndexOperationResponse());
    var expectedBulkRequest = new BulkRequest().add(deleteRequest(subject1, 123L, 1L));
    assertThat(writeToString(bulkRequestCaptor.getValue())).isEqualTo(writeToString(expectedBulkRequest));
    verify(searchConfigurationProperties).getIndexing();
  }

  private static String writeToString(BulkRequest request) {
    return request.requests().stream().map(Object::toString).collect(Collectors.joining(",", "[ ", " ]"));
  }

  @SuppressWarnings("SameParameterValue")
  private static DeleteRequest deleteRequest(String subject, Long seqNo, Long primaryTerm) {
    return new DeleteRequest(getElasticsearchIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID))
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

  private static SearchConfigurationProperties searchConfigurationProperties() {
    var subjectInstanceConfiguration = new InstanceSubjectsIndexingSettings();
    var indexingSettings = new IndexingSettings();
    indexingSettings.setInstanceSubjects(subjectInstanceConfiguration);
    var props = new SearchConfigurationProperties();
    props.setIndexing(indexingSettings);
    return props;
  }

  private static SearchRequest subjectIdsRequest(String... subjects) {
    var ids = Arrays.stream(subjects).map(DigestUtils::sha256Hex).toArray(String[]::new);
    var query = searchSource().query(idsQuery().addIds(ids)).from(0).size(ids.length).fetchSource(false);
    return new SearchRequest(getElasticsearchIndexName(INSTANCE_SUBJECT_RESOURCE, TENANT_ID)).source(query);
  }

  private static SearchResponse searchResponseForSubjectIds(Map<String, Pair<Long, Long>> seqNumbers) {
    var searchHits = seqNumbers.entrySet().stream()
      .map(entry -> jsonObject(
        "_id", sha256Hex(entry.getKey()),
        "_seq_no", entry.getValue().getFirst(),
        "_primary_term", entry.getValue().getSecond()))
      .toArray(Object[]::new);

    return searchResponseFromJson(searchResponseJsonObject(searchHits));
  }

  private static ObjectNode searchResponseJsonObject(Object... searchHits) {
    return jsonObject(
      "took", 0, "timed_out", false,
      "_shards", jsonObject("total", 1, "successful", 1, "skipped", 0, "failed", 0),
      "hits", jsonObject(
        "total", jsonObject("value", searchHits.length, "relation", "eq"),
        "max_score", 0.0, "hits", jsonArray(searchHits))
    );
  }
}
