package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.search.model.service.CqlResourceIdsRequest.INSTANCE_ID_PATH;
import static org.folio.support.TestConstants.INDEX_NAME;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.utils.JsonTestUtils.asJsonString;
import static org.folio.support.utils.TestUtils.array;
import static org.folio.support.utils.TestUtils.mapOf;
import static org.folio.support.utils.TestUtils.randomId;
import static org.folio.support.utils.TestUtils.searchServiceRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.opensearch.index.query.QueryBuilders.matchAllQuery;
import static org.opensearch.search.SearchHit.createFromMap;
import static org.opensearch.search.builder.SearchSourceBuilder.searchSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.folio.search.domain.dto.Instance;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.types.ResourceType;
import org.folio.search.utils.SearchUtils;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.ClearScrollResponse;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.MultiSearchResponse.Item;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchRepositoryTest {

  private static final String SCROLL_ID = randomId();
  private static final TimeValue KEEP_ALIVE_INTERVAL = TimeValue.timeValueMinutes(1L);

  @InjectMocks
  private SearchRepository searchRepository;
  @Mock
  private RestHighLevelClient esClient;
  @Mock
  private SearchResponse searchResponse;
  @Mock
  private RetryTemplate retryTemplate;
  @Mock
  private IndexNameProvider indexNameProvider;

  @BeforeEach
  void setUp() {
    lenient().when(indexNameProvider.getIndexName(any(ResourceRequest.class)))
      .thenAnswer(invocation -> SearchUtils.getIndexName(invocation.<ResourceRequest>getArgument(0)));
  }

  @Test
  void search_positive() throws IOException {
    var searchSource = searchSource();
    var esSearchRequest = new SearchRequest().indices(INDEX_NAME).source(searchSource);

    when(esClient.search(esSearchRequest, DEFAULT)).thenReturn(searchResponse);

    var searchRequest = searchServiceRequest(Instance.class, "query");
    var actual = searchRepository.search(searchRequest, searchSource);
    assertThat(actual).isEqualTo(searchResponse);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void streamResourceIds_positive() throws Throwable {
    var searchIds = randomIds();
    var scrollIds = randomIds();
    when(retryTemplate.execute(any(RetryCallback.class))).thenAnswer(
      invocation -> invocation.<RetryCallback>getArgument(0).doWithRetry(null));
    doReturn(searchResponse(searchIds)).when(esClient).search(searchRequest(), DEFAULT);
    doReturn(searchResponse(scrollIds), searchResponse(emptyList())).when(esClient).scroll(scrollRequest(), DEFAULT);
    doReturn(new ClearScrollResponse(true, 0)).when(esClient).clearScroll(any(ClearScrollRequest.class), eq(DEFAULT));

    var request = CqlResourceIdsRequest.of(ResourceType.INSTANCE, TENANT_ID, "query", INSTANCE_ID_PATH);
    var actualIds = new ArrayList<List<String>>();

    searchRepository.streamResourceIds(request, searchSource(), actualIds::add);

    assertThat(actualIds).containsExactly(searchIds, scrollIds);
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void streamResourceIds_negative_clearScrollFailed() throws Throwable {
    when(retryTemplate.execute(any(RetryCallback.class))).thenAnswer(
      invocation -> invocation.<RetryCallback>getArgument(0).doWithRetry(null));
    var searchIds = randomIds();
    doReturn(searchResponse(searchIds)).when(esClient).search(searchRequest(), DEFAULT);
    doReturn(searchResponse(emptyList())).when(esClient).scroll(scrollRequest(), DEFAULT);
    doReturn(new ClearScrollResponse(false, 0)).when(esClient).clearScroll(any(ClearScrollRequest.class), eq(DEFAULT));

    var request = CqlResourceIdsRequest.of(ResourceType.INSTANCE, TENANT_ID, "query", INSTANCE_ID_PATH);
    var actualIds = new ArrayList<String>();

    searchRepository.streamResourceIds(request, searchSource(), actualIds::addAll);

    assertThat(actualIds).isEqualTo(searchIds);
  }

  @Test
  void msearch_positive() throws IOException {
    var searchSource1 = searchSource().query(matchAllQuery()).from(0).size(10);
    var searchSource2 = searchSource().query(matchAllQuery()).from(10).size(10);
    var multiSearchRequest = new MultiSearchRequest();
    multiSearchRequest.add(new SearchRequest().indices(INDEX_NAME).source(searchSource1));
    multiSearchRequest.add(new SearchRequest().indices(INDEX_NAME).source(searchSource2));

    var multiSearchResponse = mock(MultiSearchResponse.class);
    when(esClient.msearch(multiSearchRequest, DEFAULT)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(array(mock(Item.class), mock(Item.class)));

    var searchRequest = searchServiceRequest(Instance.class, "query");
    var actual = searchRepository.msearch(searchRequest, List.of(searchSource1, searchSource2));
    assertThat(actual).isEqualTo(multiSearchResponse);
  }

  @Test
  void msearch_negative_oneOfSearchRequestsFailWithError() throws IOException {
    var searchSource1 = searchSource().query(matchAllQuery()).from(0).size(10);
    var searchSource2 = searchSource().query(matchAllQuery()).from(10).size(10);
    var multiSearchRequest = new MultiSearchRequest();
    multiSearchRequest.add(new SearchRequest().indices(INDEX_NAME).source(searchSource1));
    multiSearchRequest.add(new SearchRequest().indices(INDEX_NAME).source(searchSource2));

    var multiSearchResponse = mock(MultiSearchResponse.class);
    var responseItem = mock(Item.class);
    when(esClient.msearch(multiSearchRequest, DEFAULT)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(array(mock(Item.class), responseItem));
    when(responseItem.getFailure()).thenReturn(new Exception("error"));
    when(responseItem.getFailureMessage()).thenReturn("all-shards failed");

    var searchRequest = searchServiceRequest(Instance.class, "query");
    var searchSources = List.of(searchSource1, searchSource2);

    assertThatThrownBy(() -> searchRepository.msearch(searchRequest, searchSources)).isInstanceOf(
        SearchServiceException.class)
      .hasMessage("Failed to perform multi-search operation [errors: [all-shards failed]]");
  }

  @Test
  void msearch_negative_onlyOneSearchResponseReturned() throws IOException {
    var searchSource1 = searchSource().query(matchAllQuery()).from(0).size(10);
    var searchSource2 = searchSource().query(matchAllQuery()).from(10).size(10);
    var multiSearchRequest = new MultiSearchRequest();
    multiSearchRequest.add(new SearchRequest().indices(INDEX_NAME).source(searchSource1));
    multiSearchRequest.add(new SearchRequest().indices(INDEX_NAME).source(searchSource2));

    var multiSearchResponse = mock(MultiSearchResponse.class);
    when(esClient.msearch(multiSearchRequest, DEFAULT)).thenReturn(multiSearchResponse);
    when(multiSearchResponse.getResponses()).thenReturn(array(mock(Item.class)));

    var searchRequest = searchServiceRequest(Instance.class, "query");
    var searchSources = List.of(searchSource1, searchSource2);

    assertThatThrownBy(() -> searchRepository.msearch(searchRequest, searchSources)).isInstanceOf(
      SearchServiceException.class).hasMessage("Failed to perform multi-search operation [errors: []]");
  }

  private static List<String> randomIds() {
    return IntStream.range(0, 10).mapToObj(i -> randomId()).toList();
  }

  private static SearchRequest searchRequest() {
    return new SearchRequest().indices(INDEX_NAME).source(searchSource()).scroll(KEEP_ALIVE_INTERVAL);
  }

  private static SearchScrollRequest scrollRequest() {
    return new SearchScrollRequest(SCROLL_ID).scroll(KEEP_ALIVE_INTERVAL);
  }

  private static SearchResponse searchResponse(List<String> ids) {
    var totalHits = new TotalHits(20L, Relation.EQUAL_TO);
    var searchHitsArray = ids.stream()
      .map(id -> createFromMap(mapOf("_id", id, "_source", new BytesArray(asJsonString(mapOf("id", id))))))
      .toArray(SearchHit[]::new);
    var searchHits = new SearchHits(searchHitsArray, totalHits, 10.0f);
    var searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
    return new SearchResponse(searchResponseSections, SCROLL_ID, 1, 1, 0, 100, array(), null);
  }
}
