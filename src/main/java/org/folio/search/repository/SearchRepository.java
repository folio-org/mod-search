package org.folio.search.repository;

import static java.util.Arrays.stream;
import static org.apache.commons.lang3.ArrayUtils.isNotEmpty;
import static org.folio.search.configuration.RetryTemplateConfiguration.STREAM_IDS_RETRY_TEMPLATE_NAME;
import static org.folio.search.utils.CollectionUtils.anyMatch;
import static org.folio.search.utils.CollectionUtils.getValuesByPath;
import static org.folio.search.utils.SearchUtils.performExceptionalOperation;
import static org.opensearch.client.RequestOptions.DEFAULT;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.folio.search.exception.SearchServiceException;
import org.folio.search.model.ResourceRequest;
import org.folio.search.model.service.CqlResourceIdsRequest;
import org.folio.search.model.types.ResourceType;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.MultiSearchRequest;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.MultiSearchResponse.Item;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.AnalyzeRequest;
import org.opensearch.client.indices.AnalyzeResponse;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.search.Scroll;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Repository;

/**
 * Search resource repository with set of operation to perform search operations.
 */
@Repository
@RequiredArgsConstructor
public class SearchRepository {

  private static final TimeValue KEEP_ALIVE_INTERVAL = TimeValue.timeValueMinutes(1L);
  private static final String SEARCH_OPERATION_TYPE = "searchApi";
  private static final String ANALYZE_OPERATION_TYPE = "analyzeApi";
  private final RestHighLevelClient client;
  @Qualifier(value = STREAM_IDS_RETRY_TEMPLATE_NAME)
  private final RetryTemplate retryTemplate;
  private final IndexNameProvider indexNameProvider;

  public String analyze(String text, String field, ResourceType resource, String tenantId) {
    var index = indexNameProvider.getIndexName(resource, tenantId);
    var analyzeRequest = AnalyzeRequest.withField(index, field, text);
    var analyzeResponse = performExceptionalOperation(() -> client.indices().analyze(analyzeRequest, DEFAULT), index,
      ANALYZE_OPERATION_TYPE);
    return analyzeResponse.getTokens().stream()
      .map(AnalyzeResponse.AnalyzeToken::getTerm)
      .filter(Objects::nonNull)
      .collect(Collectors.joining());
  }

  /**
   * Executes request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSource    elasticsearch search source as {@link SearchSourceBuilder} object.
   * @return search result as {@link SearchResponse} object.
   */
  public SearchResponse search(ResourceRequest resourceRequest, SearchSourceBuilder searchSource) {
    var index = indexNameProvider.getIndexName(resourceRequest);
    var searchRequest = buildSearchRequest(index, searchSource);
    return performExceptionalOperation(() -> client.search(searchRequest, DEFAULT), index, SEARCH_OPERATION_TYPE);
  }

  /**
   * Executes request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSource    elasticsearch search source as {@link SearchSourceBuilder} object.
   * @param preference      elasticsearch preference string to route same requests to the same shard
   * @return search result as {@link SearchResponse} object.
   */
  public SearchResponse search(ResourceRequest resourceRequest, SearchSourceBuilder searchSource, String preference) {
    var index = indexNameProvider.getIndexName(resourceRequest);
    var searchRequest = buildSearchRequest(index, searchSource, preference);
    return performExceptionalOperation(() -> client.search(searchRequest, DEFAULT), index, SEARCH_OPERATION_TYPE);
  }

  /**
   * Executes multi-search request to elasticsearch and returns search result with related documents.
   *
   * @param resourceRequest resource request as {@link ResourceRequest} object.
   * @param searchSources   - collection with elasticsearch search source as {@link SearchSourceBuilder} object.
   * @return search result as {@link MultiSearchResponse} object.
   */
  public MultiSearchResponse msearch(ResourceRequest resourceRequest, Collection<SearchSourceBuilder> searchSources) {
    var index = indexNameProvider.getIndexName(resourceRequest);
    var request = new MultiSearchRequest();
    searchSources.forEach(source -> request.add(buildSearchRequest(index, source)));
    var response = performExceptionalOperation(() -> client.msearch(request, DEFAULT), index, "multiSearchApi");

    if (isFailedMultiSearchRequest(response.getResponses(), searchSources.size())) {
      var failureMessages = stream(response.getResponses())
        .map(Item::getFailureMessage)
        .filter(Objects::nonNull)
        .toList();

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
    var index = indexNameProvider.getIndexName(req);
    var searchRequest = new SearchRequest()
      .scroll(new Scroll(KEEP_ALIVE_INTERVAL))
      .source(src)
      .indices(index);

    var searchResponse = performExceptionalOperation(
      () -> client.search(searchRequest, DEFAULT), index, SEARCH_OPERATION_TYPE);
    var scrollId = searchResponse.getScrollId();
    var searchHits = searchResponse.getHits().getHits();

    while (isNotEmpty(searchHits)) {
      consumer.accept(getResourceIds(searchHits, req.sourceFieldPath()));
      var scrollRequest = new SearchScrollRequest(scrollId).scroll(KEEP_ALIVE_INTERVAL);
      var scrollResponse = retryTemplate.invoke(() -> performExceptionalOperation(
        () -> client.scroll(scrollRequest, DEFAULT), index, "scrollApi"));
      scrollId = scrollResponse.getScrollId();
      searchHits = scrollResponse.getHits().getHits();
    }

    clearScrollAfterStreaming(index, scrollId);
  }

  private static SearchRequest buildSearchRequest(String index, SearchSourceBuilder source) {
    return new SearchRequest().source(source).indices(index);
  }

  private static SearchRequest buildSearchRequest(String index, SearchSourceBuilder source, String preference) {
    return buildSearchRequest(index, source).preference(preference);
  }

  private void clearScrollAfterStreaming(String index, String scrollId) {
    var clearScrollRequest = new ClearScrollRequest();
    clearScrollRequest.addScrollId(scrollId);
    performExceptionalOperation(() ->
      client.clearScroll(clearScrollRequest, DEFAULT), index, "scrollApi"
    );
  }

  private static List<String> getResourceIds(SearchHit[] searchHits, String sourceFieldPath) {
    return stream(searchHits)
      .map(SearchHit::getSourceAsMap)
      .map(sourceMap -> getValuesByPath(sourceMap, sourceFieldPath))
      .flatMap(Collection::stream)
      .toList();
  }

  private static boolean isFailedMultiSearchRequest(Item[] responses, int expectedCount) {
    return responses.length != expectedCount || anyMatch(List.of(responses), resp -> resp.getFailure() != null);
  }
}
