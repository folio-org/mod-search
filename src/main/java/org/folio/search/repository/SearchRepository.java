package org.folio.search.repository;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.elasticsearch.client.RequestOptions.DEFAULT;
import static org.elasticsearch.core.TimeValue.timeValueMinutes;
import static org.folio.search.utils.SearchUtils.getElasticsearchIndexName;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.folio.search.domain.dto.SearchResult;
import org.folio.search.model.ResourceRequest;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SearchRepository {

  private static final TimeValue KEEP_ALIVE_INTERVAL = timeValueMinutes(1L);
  private final RestHighLevelClient elasticsearchClient;

  /**
   * Executes request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSource elasticsearch search source as {@link SearchSourceBuilder} object.
   * @return search result as {@link SearchResult} object.
   */
  public SearchResponse search(ResourceRequest resourceRequest, SearchSourceBuilder searchSource) {
    var index = getElasticsearchIndexName(resourceRequest);
    var searchRequest = new SearchRequest()
      .routing(resourceRequest.getTenantId())
      .source(searchSource)
      .indices(index);

    return performExceptionalOperation(() -> elasticsearchClient.search(searchRequest, DEFAULT), index, "searchApi");
  }

  /**
   * Executes scroll request to elasticsearch and transforms it to the list of instance ids.
   *
   * @param request resource request as {@link ResourceRequest} object.
   * @param source elasticsearch search source as {@link SearchSourceBuilder} object.
   */
  public void streamResourceIds(ResourceRequest request, SearchSourceBuilder source, Consumer<List<String>> consumer) {
    var index = getElasticsearchIndexName(request);
    var searchRequest = new SearchRequest()
      .scroll(new Scroll(KEEP_ALIVE_INTERVAL))
      .routing(request.getTenantId())
      .source(source)
      .indices(index);

    var searchResponse = performExceptionalOperation(
      () -> elasticsearchClient.search(searchRequest, DEFAULT), index, "searchApi");
    var scrollId = searchResponse.getScrollId();
    var searchHits = searchResponse.getHits().getHits();

    while (isNotEmpty(searchHits)) {
      consumer.accept(stream(searchHits).map(SearchHit::getId).collect(toList()));
      var scrollRequest = new SearchScrollRequest(scrollId).scroll(KEEP_ALIVE_INTERVAL);
      var scrollResponse = performExceptionalOperation(
        () -> elasticsearchClient.scroll(scrollRequest, DEFAULT), index, "scrollApi");
      scrollId = scrollResponse.getScrollId();
      searchHits = scrollResponse.getHits().getHits();
    }

    clearScrollAfterStreaming(index, scrollId);
  }

  private void clearScrollAfterStreaming(String index, String scrollId) {
    var clearScrollRequest = new ClearScrollRequest();
    clearScrollRequest.addScrollId(scrollId);
    var clearScrollResponse = performExceptionalOperation(
      () -> elasticsearchClient.clearScroll(clearScrollRequest, DEFAULT), index, "scrollApi");
    if (!clearScrollResponse.isSucceeded()) {
      log.warn("Failed to clear scroll [index: {}, scrollId: '{}']", index, scrollId);
    }
  }
}
