package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static org.folio.search.utils.SearchUtils.PLAIN_FULLTEXT_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.search.model.SearchResult;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchDocumentConverter {

  private final ObjectMapper objectMapper;

  /**
   * Converts an Elasticsearch {@link SearchResponse} object into {@link SearchResult} object.
   *
   * @param response              - an Elasticsearch search response as {@link SearchResponse} object
   * @param responseClass         - type for converting source in the {@link SearchHit} object
   * @param <T>                   - generic type of conversion result for search hit source
   * @return created {@link SearchResult} object.
   */
  public <T> SearchResult<T> convertToSearchResult(SearchResponse response, Class<T> responseClass) {
    return convertToSearchResult(response, responseClass, (hit, item) -> item);
  }

  /**
   * Converts an Elasticsearch {@link SearchResponse} object into {@link SearchResult} object.
   *
   * @param response              - an Elasticsearch search response as {@link SearchResponse} object
   * @param responseClass         - type for converting source in the {@link SearchHit} object
   * @param hitMapper             - conversion {@link BiFunction} object, that used to transform hit and source into
   *                                result type
   * @param <T>                   - generic type of conversion result for search hit source
   * @param <R>                   - generic type of response item in {@link SearchResult} object
   * @return created {@link SearchResult} object.
   */
  public <T, R> SearchResult<R> convertToSearchResult(SearchResponse response,
                                                      Class<T> responseClass,  BiFunction<SearchHit, T, R> hitMapper) {
    return Optional.ofNullable(response)
      .map(SearchResponse::getHits)
      .map(hits -> SearchResult.of(
        getTotalRecords(hits), convertSearchHits(hits.getHits(), responseClass, hitMapper)))
      .orElseGet(SearchResult::empty);
  }

  /**
   * Converts elasticsearch document to the result class.
   *
   * <p>It removes all multi-language field data and leaves only src in the value,
   * removing sub-object and leaving one level only</p>
   *
   * @param <T>              generic type for result class
   * @param elasticsearchHit elasticsearch hit as {@link Map} object
   * @param resultClass      expected result class
   * @return constructed {@link T} object from incoming map.
   */
  public <T> T convert(Map<String, Object> elasticsearchHit, Class<T> resultClass) {
    if (MapUtils.isEmpty(elasticsearchHit)) {
      return objectMapper.convertValue(elasticsearchHit, resultClass);
    }
    var convertedValue = objectMapper.convertValue(processMap(elasticsearchHit), resultClass);
    elasticsearchHit.clear();
    return convertedValue;
  }

  private <T, R> List<R> convertSearchHits(SearchHit[] searchHits, Class<T> type,
                                           BiFunction<SearchHit, T, R> searchHitMapper) {
    if (searchHits == null) {
      return emptyList();
    }

    return Arrays.stream(searchHits)
      .map(searchHit -> searchHitMapper.apply(searchHit, convert(searchHit.getSourceAsMap(), type)))
      .toList();
  }

  private static Map<String, Object> processMap(Map<String, Object> map) {
    var resultMap = new LinkedHashMap<String, Object>();
    for (var entry : map.entrySet()) {
      var key = entry.getKey();
      var value = entry.getValue();
      if (key.startsWith(PLAIN_FULLTEXT_PREFIX)) {
        resultMap.put(key.substring(PLAIN_FULLTEXT_PREFIX.length()), processField(value));
        continue;
      }
      resultMap.computeIfAbsent(key, k -> processField(value));
    }
    return resultMap;
  }

  @SuppressWarnings("unchecked")
  private static Object processField(Object value) {
    if (value instanceof Map) {
      return processMap((Map<String, Object>) value);
    }
    if (value instanceof List) {
      List<Object> listValue = (List<Object>) value;
      return listValue.stream()
        .map(ElasticsearchDocumentConverter::processField)
        .filter(Objects::nonNull)
        .toList();
    }
    return value;
  }

  private static int getTotalRecords(SearchHits hits) {
    var totalHits = hits.getTotalHits();
    return totalHits != null ? (int) totalHits.value : 0;
  }
}
