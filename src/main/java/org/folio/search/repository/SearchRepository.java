package org.folio.search.repository;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.opensearch.client.RequestOptions.DEFAULT;
import static org.folio.search.configuration.RetryTemplateConfiguration.STREAM_IDS_RETRY_TEMPLATE_NAME;
import static org.folio.search.utils.CollectionUtils.anyMatch;
import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.SearchUtils.getIndexName;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.MultiSearchResponse.Item;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.search.Scroll;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SearchRepository {

  private static final TimeValue KEEP_ALIVE_INTERVAL = TimeValue.timeValueMinutes(1L);
  private final RestHighLevelClient client;
  @Qualifier(value = STREAM_IDS_RETRY_TEMPLATE_NAME)
  private final RetryTemplate retryTemplate;

  /**
   * Executes request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSource elasticsearch search source as {@link SearchSourceBuilder} object.
   * @return search result as {@link SearchResponse} object.
   */
  public SearchResponse search(ResourceRequest resourceRequest, SearchSourceBuilder searchSource) {
    var index = getIndexName(resourceRequest);
    var searchRequest = buildSearchRequest(resourceRequest, index, searchSource);
    return performExceptionalOperation(() -> client.search(searchRequest, DEFAULT), index, "searchApi");
  }

  /**
   * Executes multi-search request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSources - collection with elasticsearch search source as {@link SearchSourceBuilder} object.
   * @return search result as {@link MultiSearchResponse} object.
   */
  public MultiSearchResponse msearch(ResourceRequest resourceRequest, Collection<SearchSourceBuilder> searchSources) {
    var index = getIndexName(resourceRequest);
    var request = new MultiSearchRequest();
    searchSources.forEach(source -> request.add(buildSearchRequest(resourceRequest, index, source)));
    var response = performExceptionalOperation(() -> client.msearch(request, DEFAULT), index, "multiSearchApi");

    if (isFailedMultiSearchRequest(response.getResponses(), searchSources.size())) {
      var failureMessages = stream(response.getResponses())
        .map(Item::getFailureMessage)
        .filter(Objects::nonNull)
        .collect(toList());

      throw new SearchServiceException(String.format(
        "Failed to perform multi-search operation [errors: %s]", failureMessages));
    }

    return response;
  }

  /**
   * Executes scroll request to elasticsearch and transforms it to the list of instance ids.
   *
   * @param req - request as {@link CqlResourceIdsRequest} object.
   * @param src - elasticsearch search query source as {@link SearchSourceBuilder} object.
   */
  public void streamResourceIds(CqlResourceIdsRequest req, SearchSourceBuilder src, Consumer<List<String>> consumer) {
    var index = getIndexName(req);
    var searchRequest = new SearchRequest()
      .scroll(new Scroll(KEEP_ALIVE_INTERVAL))
      .routing(req.getTenantId())
      .source(src)
      .indices(index);

    var searchResponse = performExceptionalOperation(
      () -> client.search(searchRequest, DEFAULT), index, "searchApi");
    var scrollId = searchResponse.getScrollId();
    var searchHits = searchResponse.getHits().getHits();

    while (isNotEmpty(searchHits)) {
      consumer.accept(getResourceIds(searchHits, req.getSourceFieldPath()));
      var scrollRequest = new SearchScrollRequest(scrollId).scroll(KEEP_ALIVE_INTERVAL);
      var scrollResponse = retryTemplate.execute(v -> performExceptionalOperation(
        () -> client.scroll(scrollRequest, DEFAULT), index, "scrollApi"));
      scrollId = scrollResponse.getScrollId();
      searchHits = scrollResponse.getHits().getHits();
    }

    clearScrollAfterStreaming(index, scrollId);
  }

  private static SearchRequest buildSearchRequest(ResourceRequest request, String index, SearchSourceBuilder source) {
    return new SearchRequest().routing(request.getTenantId()).source(source).indices(index);
  }

  private void clearScrollAfterStreaming(String index, String scrollId) {
    var clearScrollRequest = new ClearScrollRequest();
    clearScrollRequest.addScrollId(scrollId);
    var clearScrollResponse = performExceptionalOperation(
      () -> client.clearScroll(clearScrollRequest, DEFAULT), index, "scrollApi");
    if (!clearScrollResponse.isSucceeded()) {
      log.warn("Failed to clear scroll [index: {}, scrollId: '{}']", index, scrollId);
    }
  }

  private static List<String> getResourceIds(SearchHit[] searchHits, String sourceFieldPath) {
    return stream(searchHits)
      .map(SearchHit::getSourceAsMap)
      .map(sourceMap -> getValuesByPath(sourceMap, sourceFieldPath))
      .flatMap(Collection::stream)
      .collect(toList());
  }

  private static boolean isFailedMultiSearchRequest(Item[] responses, int expectedCount) {
    return responses.length != expectedCount || anyMatch(List.of(responses), resp -> resp.getFailure() != null);
  }
}
