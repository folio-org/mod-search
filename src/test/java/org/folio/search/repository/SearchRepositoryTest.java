package org.folio.search.repository;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.elasticsearch.search.builder.SearchSourceBuilder.searchSource;
import static org.folio.search.utils.TestConstants.INDEX_NAME;
import static org.folio.search.utils.TestConstants.RESOURCE_NAME;
import static org.folio.search.utils.TestConstants.TENANT_ID;
import static org.folio.search.utils.TestUtils.array;
import static org.folio.search.utils.TestUtils.randomId;
import static org.folio.search.utils.TestUtils.searchServiceRequest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.search.TotalHits.Relation;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchResponseSections;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.utils.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SearchRepositoryTest {

  private static final String SCROLL_ID = randomId();
  private static final TimeValue KEEP_ALIVE_INTERVAL = TimeValue.timeValueMinutes(1L);

  @InjectMocks private SearchRepository searchRepository;
  @Mock private RestHighLevelClient esClient;
  @Mock private SearchResponse searchResponse;

  @Test
  void search_positive() throws IOException {
    var searchSource = searchSource();
    var esSearchRequest = new SearchRequest().indices(INDEX_NAME).routing(TENANT_ID).source(searchSource);

    when(esClient.search(esSearchRequest, DEFAULT)).thenReturn(searchResponse);

    var searchRequest = searchServiceRequest(RESOURCE_NAME, "query");
    var actual = searchRepository.search(searchRequest, searchSource);
    assertThat(actual).isEqualTo(searchResponse);
  }

  @Test
  void streamResourceIds_positive() throws Throwable {
    var searchIds = randomIds();
    var scrollIds = randomIds();

    doReturn(searchResponse(searchIds)).when(esClient).search(searchRequest(), DEFAULT);
    doReturn(searchResponse(scrollIds), searchResponse(emptyList())).when(esClient).scroll(scrollRequest(), DEFAULT);
    doReturn(clearScrollResponse(true)).when(esClient).clearScroll(any(ClearScrollRequest.class), eq(DEFAULT));

    var request = CqlResourceIdsRequest.of("query", RESOURCE_NAME, TENANT_ID);
    var actualIds = new ArrayList<List<String>>();

    searchRepository.streamResourceIds(request, searchSource(), actualIds::add);

    assertThat(actualIds).containsExactly(searchIds, scrollIds);
  }

  @Test
  void streamResourceIds_negative_clearScrollFailed() throws Throwable {
    var searchIds = randomIds();
    doReturn(searchResponse(searchIds)).when(esClient).search(searchRequest(), DEFAULT);
    doReturn(searchResponse(emptyList())).when(esClient).scroll(scrollRequest(), DEFAULT);
    doReturn(clearScrollResponse(false)).when(esClient).clearScroll(any(ClearScrollRequest.class), eq(DEFAULT));

    var request = CqlResourceIdsRequest.of("query", RESOURCE_NAME, TENANT_ID);
    var actualIds = new ArrayList<String>();

    searchRepository.streamResourceIds(request, searchSource(), actualIds::addAll);

    assertThat(actualIds).isEqualTo(searchIds);
  }

  private static List<String> randomIds() {
    return IntStream.range(0, 10).mapToObj(i -> randomId()).collect(toList());
  }

  private static SearchRequest searchRequest() {
    return new SearchRequest().indices(INDEX_NAME).routing(TENANT_ID)
      .source(searchSource()).scroll(KEEP_ALIVE_INTERVAL);
  }

  private static SearchScrollRequest scrollRequest() {
    return new SearchScrollRequest(SCROLL_ID).scroll(KEEP_ALIVE_INTERVAL);
  }

  private static SearchResponse searchResponse(List<String> ids) {
    var totalHits = new TotalHits(20L, Relation.EQUAL_TO);
    var searchHits = new SearchHits(searchHits(ids), totalHits, 10.0f);
    var searchResponseSections = new SearchResponseSections(searchHits, null, null, false, false, null, 0);
    return new SearchResponse(searchResponseSections, SCROLL_ID, 1, 1, 0, 100, array(), null);
  }

  private static SearchHit[] searchHits(List<String> ids) {
    return ids.stream()
      .map(id -> new SearchHit(0, id, new Text("_doc"), emptyMap(), emptyMap()))
      .toArray(SearchHit[]::new);
  }

  private static ClearScrollResponse clearScrollResponse(boolean isSucceeded) {
    return new ClearScrollResponse(isSucceeded, 0);
  }
}
