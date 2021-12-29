package org.folio.search.service.converter;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.folio.search.utils.SearchUtils.PLAIN_FULLTEXT_PREFIX;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.folio.search.model.SearchResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ElasticsearchDocumentConverter {

  private final ObjectMapper objectMapper;

  /**
   * Converts an Elasticsearch {@link SearchResponse} object into {@link SearchResult} object.
   *
   * @param response - an Elasticsearch search response as {@link SearchResponse} object
   * @return created {@link SearchResult} object.
   */
  public <T> SearchResult<T> convertToSearchResult(SearchResponse response, Class<T> responseClass) {
    return Optional.ofNullable(response)
      .map(SearchResponse::getHits)
      .map(hits -> SearchResult.of(getTotalRecords(hits), convertSearchHits(hits.getHits(), responseClass)))
      .orElseGet(SearchResult::empty);
  }

  /**
   * Converts elasticsearch document to the result class.
   *
   * <p>It removes all multi-language field data and leaves only src in the value,
   * removing sub-object and leaving one level only</p>
   *
   * @param <T> generic type for result class
   * @param elasticsearchHit elasticsearch hit as {@link Map} object
   * @param resultClass expected result class
   * @return constructed {@link T} object from incoming map.
   */
  public <T> T convert(Map<String, Object> elasticsearchHit, Class<T> resultClass) {
    if (MapUtils.isEmpty(elasticsearchHit)) {
      return objectMapper.convertValue(elasticsearchHit, resultClass);
    }
    return objectMapper.convertValue(processMap(elasticsearchHit), resultClass);
  }

  private  <T> List<T> convertSearchHits(SearchHit[] searchHits, Class<T> type) {
    if (searchHits == null) {
      return emptyList();
    }
    return Arrays.stream(searchHits)
      .map(SearchHit::getSourceAsMap)
      .map(map -> convert(map, type))
      .collect(toList());
  }

  private static Map<String, Object> processMap(Map<String, Object> map) {
    var resultMap = new LinkedHashMap<String, Object>();
    for (var entry : map.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith(PLAIN_FULLTEXT_PREFIX)) {
        resultMap.put(key.substring(PLAIN_FULLTEXT_PREFIX.length()), processField(entry.getValue()));
        continue;
      }
      resultMap.putIfAbsent(key, processField(entry.getValue()));
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
        .collect(Collectors.toList());
    }
    return value;
  }

  private static int getTotalRecords(SearchHits hits) {
    var totalHits = hits.getTotalHits();
    return totalHits != null ? (int) totalHits.value : 0;
  }
}
